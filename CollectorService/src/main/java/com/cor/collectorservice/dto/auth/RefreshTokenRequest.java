package com.cor.collectorservice.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Запрос, содержащий refresh-токен: используется при продлении сессии и при выходе из системы.
 *
 * @param refreshToken refresh-токен, полученный при входе в систему
 */
@Schema(description = "Запрос с refresh-токеном")
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh-токен обязателен")
        @Schema(description = "Refresh-токен, выданный при входе",
                example = "eyJhbGciOiJIUzUxMiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ...",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken
) {
}
