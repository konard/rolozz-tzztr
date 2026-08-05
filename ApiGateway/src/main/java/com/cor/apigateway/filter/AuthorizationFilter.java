package com.cor.apigateway.filter;

import com.cor.apigateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Фильтр авторизации для API Gateway.
 * Проверяет роли пользователя для доступа к различным эндпоинтам.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationFilter implements GatewayFilter {

    private final JwtUtil jwtUtil;

    /**
     * Выполняет фильтрацию запроса.
     * Проверяет роли пользователя и разрешает или запрещает доступ к эндпоинтам.
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

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        String token = authHeader.substring(7);
        List<String> userRoles = jwtUtil.getRoles(token);

        if (userRoles == null || userRoles.isEmpty()) {
            log.warn("Роли не найдены в JWT токене для пути: {}", path);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return response.setComplete();
        }

        // Проверка доступа к admin контроллерам
        if (isAdminPath(path)) {
            if (!hasAdminAccess(userRoles)) {
                log.warn("Доступ запрещен к админскому пути: {} для ролей: {}", path, userRoles);
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return response.setComplete();
            }
        }

        // Все остальные эндпоинты доступны для всех ролей
        log.debug("Авторизация успешна для пути: {} с ролями: {}", path, userRoles);
        return chain.filter(exchange);
    }

    /**
     * Проверяет, является ли путь административным.
     * 
     * @param path путь запроса
     * @return true если путь административный, иначе false
     */
    private boolean isAdminPath(String path) {
        return path.contains("/admin") || path.contains("/admin/") || path.endsWith("/admin");
    }

    /**
     * Проверяет, есть ли у пользователя права администратора.
     * 
     * @param roles список ролей пользователя
     * @return true если есть права администратора, иначе false
     */
    private boolean hasAdminAccess(List<String> roles) {
        return roles.contains("ADMIN") || roles.contains("OWNER");
    }
}
