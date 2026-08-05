package com.cor.apigateway.filter;

import com.cor.apigateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Фильтр аутентификации для API Gateway.
 * Проверяет JWT токен в запросе и добавляет пользовательские данные в хэдеры.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GatewayFilter {

    private final JwtUtil jwtUtil;

    /**
     * Выполняет фильтрацию запроса.
     * Валидирует JWT токен и добавляет пользовательские данные в хэдеры.
     * 
     * @param exchange обмен данными между клиентом и сервером
     * @param chain цепочка фильтров
     * @return Mono<Void> для асинхронной обработки
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        String path = request.getURI().getPath();

        // Получаем Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Отсутствует или некорректный Authorization header для пути: {}", path);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        String token = authHeader.substring(7);

        // Валидация токена
        if (!jwtUtil.validateToken(token)) {
            log.warn("Неверный JWT токен для пути: {}", path);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        // Извлечение данных из токена
        String userId = jwtUtil.getUserId(token);
        String username = jwtUtil.getUsername(token);

        if (userId == null || username == null) {
            log.warn("Не удалось извлечь пользовательские данные из JWT токена");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        // Добавляем user ID и username в headers для downstream сервисов
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", userId)
                .header("X-Username", username)
                .build();

        log.debug("Аутентификация успешна для пользователя: {} ({})", username, userId);
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }
}
