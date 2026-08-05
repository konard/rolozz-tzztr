package com.cor.apigateway.config;

import com.cor.apigateway.filter.AuthenticationFilter;
import com.cor.apigateway.filter.AuthorizationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Конфигурация API Gateway.
 * Настраивает маршрутизацию запросов к микросервисам и применяет фильтры безопасности.
 */
@Configuration
@RequiredArgsConstructor
public class GatewayConfig {

    private final AuthenticationFilter authenticationFilter;
    private final AuthorizationFilter authorizationFilter;

    @Value("${services.user-service-url}")
    private String userServiceUrl;

    /**
     * Настраивает маршруты API Gateway.
     * 
     * @param builder строитель маршрутов
     * @return настроенный локатор маршрутов
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Публичный маршрут регистрации (без JWT фильтров)
                .route("user-service-registration", r -> r
                        .path("/api/v1/user-profiles/create")
                        .and()
                        .method(HttpMethod.POST)
                        .uri(userServiceUrl))

                // Маршруты к UserService с аутентификацией и авторизацией
                .route("user-service", r -> r
                        .path("/api/v1/user-profiles/**")
                        .filters(f -> f
                                .filter(authenticationFilter)
                                .filter(authorizationFilter))
                        .uri(userServiceUrl))
                
                // Административные маршруты с аутентификацией и авторизацией
                .route("user-service-admin", r -> r
                        .path("/api/admin/**")
                        .filters(f -> f
                                .filter(authenticationFilter)
                                .filter(authorizationFilter))
                        .uri(userServiceUrl))
                
                // Агрегация Swagger документации для UserService
                .route("user-service-swagger", r -> r
                        .path("/api-docs", "/swagger-ui.html", "/swagger-ui/**")
                        .filters(f -> f
                                .rewritePath("/api-docs", "/api-docs")
                                .rewritePath("/swagger-ui/(?<segment>.*)", "/swagger-ui/${segment}"))
                        .uri(userServiceUrl))
                
                .build();
    }
}
