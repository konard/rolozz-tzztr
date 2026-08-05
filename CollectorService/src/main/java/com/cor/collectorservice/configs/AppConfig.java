package com.cor.collectorservice.configs;


import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Semaphore;

@Configuration
public class AppConfig {

    /**
     * Имя схемы авторизации в OpenAPI: access-токен Keycloak в заголовке {@code Authorization}.
     */
    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * Единый семафор, гарантирующий, что запросы карточек к WB API выполняются
     * строго по одному: пока для одного пользователя идёт загрузка, остальные
     * встают в очередь (честный режим — FIFO). Общий для всех пользователей.
     */
    @Bean
    public Semaphore wbApiSemaphore() {
        return new Semaphore(1, true);
    }

    /**
     * Описание REST API сервиса для Swagger UI.
     * <p>
     * Аутентификация построена на access-токенах Keycloak: токен выдаёт эндпоинт
     * {@code POST /api/auth/login}, после чего его нужно передавать в заголовке
     * {@code Authorization: Bearer <access_token>}. В Swagger UI для этого достаточно
     * нажать «Authorize» и вставить сам токен (без слова {@code Bearer}).
     *
     * @return описание OpenAPI с единственной схемой авторизации {@code bearerAuth}
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CollectorService API")
                        .description("""
                                REST API сервиса сбора данных Wildberries.

                                ## Аутентификация
                                Пользователи хранятся в Keycloak (realm `my-realm`), сервис хранит только профиль
                                пользователя, идентификатор которого совпадает с идентификатором учётной записи в Keycloak.

                                Порядок работы:
                                1. `POST /api/auth/register` — регистрация: сначала создаётся учётная запись в Keycloak,
                                   затем профиль в базе данных сервиса. Если сохранение профиля не удалось,
                                   учётная запись в Keycloak удаляется компенсирующей операцией.
                                2. `POST /api/auth/login` — вход: сервис получает у Keycloak пару токенов
                                   (`access_token` и `refresh_token`).
                                3. Все остальные запросы выполняются с заголовком `Authorization: Bearer <access_token>`.
                                4. `POST /api/auth/refresh` — обновление истёкшего access-токена по refresh-токену.
                                5. `POST /api/auth/logout` — завершение сессии в Keycloak по refresh-токену.
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("CollectorService")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access-токен Keycloak, полученный через POST /api/auth/login")));
    }
}
