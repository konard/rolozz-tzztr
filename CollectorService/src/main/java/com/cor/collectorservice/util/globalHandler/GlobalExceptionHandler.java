package com.cor.collectorservice.util.globalHandler;

import com.cor.collectorservice.dto.auth.ErrorResponse;
import com.cor.collectorservice.util.context.UserContext;
import com.cor.collectorservice.util.context.UserContextHolder;
import com.cor.collectorservice.util.exception.BaseException;
import com.cor.collectorservice.util.exception.ForbiddenAccessException;
import com.cor.collectorservice.util.exception.InvalidCredentialsException;
import com.cor.collectorservice.util.exception.InvalidTokenException;
import com.cor.collectorservice.util.exception.KeycloakException;
import com.cor.collectorservice.util.exception.RegistrationFailedException;
import com.cor.collectorservice.util.exception.UnauthorizedAccessException;
import com.cor.collectorservice.util.exception.WbApiException;
import com.cor.collectorservice.util.exception.WbRateLimitException;
import com.cor.collectorservice.util.exception.WbSyncException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Единая точка преобразования исключений в HTTP-ответы формата {@link ErrorResponse}.
 * <p>
 * После отказа от Spring Security обработчики его исключений
 * ({@code BadCredentialsException}, {@code AccessDeniedException}) заменены обработчиками
 * собственных исключений сервиса: {@link InvalidCredentialsException},
 * {@link UnauthorizedAccessException}, {@link ForbiddenAccessException}, {@link InvalidTokenException}
 * и семейства {@link KeycloakException}.
 * <p>
 * Обратите внимание: ошибки аутентификации, возникшие в
 * {@link com.cor.collectorservice.filter.JwtAuthenticationFilter}, сюда не попадают —
 * фильтр работает до Spring MVC и формирует такое же тело ответа самостоятельно.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Ошибки аутентификации: токен отсутствует, недействителен или учётные данные неверны.
     *
     * @param ex      исключение уровня аутентификации
     * @param request обрабатываемый запрос
     * @return ответ 401 с описанием причины
     */
    @ExceptionHandler({UnauthorizedAccessException.class, InvalidTokenException.class,
            InvalidCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(BaseException ex, HttpServletRequest request) {
        log.warn("Отказ в аутентификации: {} | Метод: {} | Путь: {}",
                ex.getMessage(), request.getMethod(), request.getRequestURI());
        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    /**
     * Пользователь аутентифицирован, но не имеет требуемой роли.
     *
     * @param ex      исключение проверки прав
     * @param request обрабатываемый запрос
     * @return ответ 403 с указанием требуемой роли
     */
    @ExceptionHandler(ForbiddenAccessException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenAccessException ex, HttpServletRequest request) {
        UserContext context = UserContextHolder.get();
        log.warn("Отказ в доступе: {} | Пользователь: {} | Метод: {} | Путь: {}",
                ex.getMessage(),
                context == null ? "не аутентифицирован" : context.username(),
                request.getMethod(),
                request.getRequestURI());
        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    /**
     * Ошибки взаимодействия с Keycloak: недоступность сервера идентификации,
     * отказ в создании, обновлении или удалении учётной записи.
     *
     * @param ex      исключение интеграции с Keycloak
     * @param request обрабатываемый запрос
     * @return ответ со статусом из исключения (как правило, 502 или 503)
     */
    @ExceptionHandler(KeycloakException.class)
    public ResponseEntity<ErrorResponse> handleKeycloak(KeycloakException ex, HttpServletRequest request) {
        log.error("Ошибка взаимодействия с Keycloak | Статус: {} | Метод: {} | Путь: {} | Причина: {}",
                ex.getStatus().value(), request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    /**
     * Регистрация не завершена: учётная запись создана в Keycloak, но профиль не сохранён.
     * К этому моменту компенсация уже запущена.
     *
     * @param ex      исключение регистрации
     * @param request обрабатываемый запрос
     * @return ответ 500 с предложением повторить регистрацию
     */
    @ExceptionHandler(RegistrationFailedException.class)
    public ResponseEntity<ErrorResponse> handleRegistrationFailed(RegistrationFailedException ex,
                                                                  HttpServletRequest request) {
        log.error("Регистрация не завершена, выполнена компенсация | Путь: {} | Причина: {}",
                request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    /**
     * Прочие бизнес-исключения сервиса.
     *
     * @param ex      исключение с заранее определённым HTTP-статусом
     * @param request обрабатываемый запрос
     * @return ответ со статусом из исключения
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex, HttpServletRequest request) {
        log.warn("Бизнес-исключение: {} | Статус: {} | Метод: {} | Путь: {}",
                ex.getMessage(), ex.getStatus().value(), request.getMethod(), request.getRequestURI());
        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    /**
     * Тело запроса не прошло проверку аннотациями {@code jakarta.validation}.
     *
     * @param ex      исключение валидации
     * @param request обрабатываемый запрос
     * @return ответ 400 с перечнем полей и причин отказа
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex,
                                                                   HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + describe(error))
                .collect(Collectors.joining("; "));
        log.warn("Ошибка валидации запроса: {} | Метод: {} | Путь: {}",
                message, request.getMethod(), request.getRequestURI());
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Тело запроса отсутствует или не является корректным JSON.
     *
     * @param ex      исключение разбора тела запроса
     * @param request обрабатываемый запрос
     * @return ответ 400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        log.warn("Некорректное тело запроса | Метод: {} | Путь: {} | Причина: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Тело запроса отсутствует или имеет некорректный формат", request);
    }

    /**
     * Превышен лимит запросов к WB API.
     *
     * @param ex      исключение лимита запросов
     * @param request обрабатываемый запрос
     * @return ответ 429
     */
    @ExceptionHandler(WbRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleWbRateLimit(WbRateLimitException ex, HttpServletRequest request) {
        log.warn("Превышен лимит запросов к WB API | Путь: {}", request.getRequestURI());
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    /**
     * Ошибка обращения к WB API.
     *
     * @param ex      исключение WB API
     * @param request обрабатываемый запрос
     * @return ответ 502
     */
    @ExceptionHandler(WbApiException.class)
    public ResponseEntity<ErrorResponse> handleWbApi(WbApiException ex, HttpServletRequest request) {
        log.error("Ошибка WB API | Путь: {} | Ошибка: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    /**
     * Ошибка синхронизации данных с WB API.
     *
     * @param ex      исключение синхронизации
     * @param request обрабатываемый запрос
     * @return ответ 500
     */
    @ExceptionHandler(WbSyncException.class)
    public ResponseEntity<ErrorResponse> handleWbSync(WbSyncException ex, HttpServletRequest request) {
        log.error("Ошибка синхронизации с WB API | Путь: {} | Ошибка: {}",
                request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request);
    }

    /**
     * Непредвиденная ошибка: наружу отдаётся обобщённое сообщение, подробности остаются в логах.
     *
     * @param ex      необработанное исключение
     * @param request обрабатываемый запрос
     * @return ответ 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Непредвиденная ошибка на сервере | Метод: {} | Путь: {} | Ошибка: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", request);
    }

    /**
     * Возвращает текст ошибки поля, подставляя обобщённое описание, если сообщение не задано.
     *
     * @param error ошибка валидации поля
     * @return текст ошибки
     */
    private String describe(FieldError error) {
        return error.getDefaultMessage() == null ? "значение некорректно" : error.getDefaultMessage();
    }

    /**
     * Собирает тело ответа об ошибке.
     *
     * @param status  HTTP-статус ответа
     * @param message описание ошибки для клиента
     * @param request обрабатываемый запрос
     * @return ответ с телом {@link ErrorResponse}
     */
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status,
                                                        String message,
                                                        HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
