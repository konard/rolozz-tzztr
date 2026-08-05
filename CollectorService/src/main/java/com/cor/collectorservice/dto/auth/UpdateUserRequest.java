package com.cor.collectorservice.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Запрос на обновление профиля текущего пользователя.
 * <p>
 * Все поля необязательны, но хотя бы одно должно быть заполнено — иначе сервис
 * ответит кодом 400. Логин и e-mail обновляются одновременно в Keycloak и в базе данных
 * сервиса, пароль — только в Keycloak, WB-токен — только в базе данных сервиса.
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Запрос на обновление профиля. Достаточно указать любое одно поле")
public class UpdateUserRequest {

    @Size(min = 3, max = 50)
    @Schema(description = "Новый логин; должен быть свободен", example = "new_username")
    String username;

    @Size(min = 6, max = 100)
    @Schema(description = "Новый пароль; сохраняется только в Keycloak", example = "new_password123")
    String password;

    @Email
    @Size(max = 500)
    @Schema(description = "Новый e-mail", example = "john_doe@example.com")
    String email;

    @Size(max = 500)
    @Schema(description = "Новый токен Wildberries; хранится в зашифрованном виде", example = "wb_token_value")
    String wbToken;
}
