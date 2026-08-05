package com.cor.collectorservice.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Пара токенов, выданная Keycloak по протоколу OpenID Connect.
 * <p>
 * Возвращается эндпоинтами {@code POST /api/auth/login} и {@code POST /api/auth/refresh}.
 * Access-токен передаётся в заголовке {@code Authorization: Bearer <access_token>},
 * refresh-токен используется для продления сессии и для выхода из системы.
 *
 * @param accessToken       access-токен (JWT), которым подписываются все защищённые запросы
 * @param expiresIn         срок жизни access-токена в секундах
 * @param refreshToken      refresh-токен для получения нового access-токена
 * @param refreshExpiresIn  срок жизни refresh-токена в секундах
 * @param tokenType         тип токена, всегда {@code Bearer}
 * @param scope             перечень выданных областей доступа
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Пара токенов Keycloak")
public record TokenResponse(

        @JsonProperty("access_token")
        @Schema(description = "Access-токен (JWT) для заголовка Authorization",
                example = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ...")
        String accessToken,

        @JsonProperty("expires_in")
        @Schema(description = "Срок жизни access-токена в секундах", example = "300")
        Long expiresIn,

        @JsonProperty("refresh_token")
        @Schema(description = "Refresh-токен для продления сессии",
                example = "eyJhbGciOiJIUzUxMiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ...")
        String refreshToken,

        @JsonProperty("refresh_expires_in")
        @Schema(description = "Срок жизни refresh-токена в секундах", example = "1800")
        Long refreshExpiresIn,

        @JsonProperty("token_type")
        @Schema(description = "Тип токена", example = "Bearer")
        String tokenType,

        @JsonProperty("scope")
        @Schema(description = "Выданные области доступа", example = "profile email")
        String scope
) {
}
