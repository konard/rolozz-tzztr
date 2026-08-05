package com.cor.collectorservice.filter;

import com.cor.collectorservice.configs.KeycloakProperties;
import com.cor.collectorservice.dto.auth.ErrorResponse;
import com.cor.collectorservice.util.context.UserContext;
import com.cor.collectorservice.util.context.UserContextHolder;
import com.cor.collectorservice.util.exception.BaseException;
import com.cor.collectorservice.util.exception.UnauthorizedAccessException;
import com.cor.collectorservice.util.jwt.TokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Фильтр аутентификации по access-токену Keycloak.
 * <p>
 * Заменяет цепочку фильтров Spring Security. Для каждого запроса:
 * <ol>
 *     <li>пропускает без проверки публичные пути ({@code keycloak.jwt.public-paths}) и preflight-запросы {@code OPTIONS};</li>
 *     <li>извлекает токен из заголовка {@code Authorization: Bearer ...};</li>
 *     <li>проверяет токен через {@link TokenVerifier} и кладёт результат в {@link UserContextHolder};</li>
 *     <li>гарантированно очищает контекст после обработки запроса.</li>
 * </ol>
 * Ошибки аутентификации возникают до входа в контроллер, поэтому {@code @RestControllerAdvice}
 * их не увидит — тело ответа {@link ErrorResponse} формируется здесь же, в том же формате,
 * что и у глобального обработчика.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Префикс схемы авторизации в заголовке {@code Authorization}.
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Ключ MDC, по которому логин пользователя попадает в каждую строку лога запроса.
     */
    private static final String MDC_USERNAME = "username";

    /**
     * Ключ MDC с идентификатором пользователя.
     */
    private static final String MDC_USER_ID = "userId";

    private final TokenVerifier tokenVerifier;
    private final ObjectMapper objectMapper;
    private final List<String> publicPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * @param tokenVerifier проверка подписи и claim-ов токена
     * @param objectMapper  сериализатор тела ответа об ошибке
     * @param properties    настройки Keycloak, из которых берётся список публичных путей
     */
    public JwtAuthenticationFilter(TokenVerifier tokenVerifier,
                                   ObjectMapper objectMapper,
                                   KeycloakProperties properties) {
        this.tokenVerifier = tokenVerifier;
        this.objectMapper = objectMapper;
        this.publicPaths = properties.getJwt().getPublicPaths();
        log.info("Фильтр аутентификации JWT активирован. Публичные пути: {}", publicPaths);
    }

    /**
     * Определяет, нужно ли пропустить запрос без проверки токена.
     *
     * @param request входящий запрос
     * @return {@code true} для публичных путей и preflight-запросов CORS
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            log.trace("Preflight-запрос {} пропущен без проверки токена", request.getRequestURI());
            return true;
        }
        String path = request.getRequestURI();
        boolean publicPath = publicPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
        if (publicPath) {
            log.debug("Путь {} является публичным, проверка токена не выполняется", path);
        }
        return publicPath;
    }

    /**
     * Проверяет токен и наполняет контекст текущего пользователя на время обработки запроса.
     *
     * @param request     входящий запрос
     * @param response    ответ, в который при ошибке пишется {@link ErrorResponse}
     * @param filterChain оставшаяся цепочка фильтров
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        try {
            if (token == null) {
                log.warn("Запрос {} {} отклонён: отсутствует заголовок Authorization с Bearer-токеном",
                        request.getMethod(), request.getRequestURI());
                throw new UnauthorizedAccessException("Отсутствует access-токен");
            }

            UserContext context = tokenVerifier.verify(token);
            UserContextHolder.set(context);
            MDC.put(MDC_USERNAME, context.username());
            MDC.put(MDC_USER_ID, String.valueOf(context.userId()));
            log.debug("Запрос {} {} аутентифицирован для пользователя {}",
                    request.getMethod(), request.getRequestURI(), context.username());

            filterChain.doFilter(request, response);
        } catch (BaseException ex) {
            writeError(request, response, ex.getStatus(), ex.getMessage());
        } finally {
            UserContextHolder.clear();
            MDC.remove(MDC_USERNAME);
            MDC.remove(MDC_USER_ID);
        }
    }

    /**
     * Извлекает токен из заголовка {@code Authorization}.
     *
     * @param request входящий запрос
     * @return токен без префикса {@code Bearer} или {@code null}, если заголовок отсутствует либо пуст
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * Пишет в ответ тело ошибки в том же формате, что и глобальный обработчик исключений.
     *
     * @param request  обрабатываемый запрос (нужен для поля {@code path})
     * @param response ответ клиенту
     * @param status   HTTP-статус ошибки
     * @param message  описание ошибки на русском языке
     */
    private void writeError(HttpServletRequest request,
                            HttpServletResponse response,
                            HttpStatus status,
                            String message) throws IOException {
        log.warn("Ответ с ошибкой аутентификации: статус={}, путь={}, причина={}",
                status.value(), request.getRequestURI(), message);

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
