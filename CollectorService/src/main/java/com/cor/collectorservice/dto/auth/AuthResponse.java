package com.cor.collectorservice.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ответ на успешный вход в систему: токены Keycloak и профиль пользователя из базы сервиса.
 *
 * @param tokens пара токенов, выданная Keycloak
 * @param user   профиль пользователя, хранящийся в CollectorService
 */
@Schema(description = "Результат входа: токены и профиль пользователя")
public record AuthResponse(

        @Schema(description = "Токены Keycloak")
        TokenResponse tokens,

        @Schema(description = "Профиль пользователя")
        UserResponse user
) {
}
