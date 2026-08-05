package com.cor.collectorservice.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Запрос на обновление профиля")
public class UpdateUserRequest {

    @Size(min = 3, max = 50)
    @Schema(example = "new_username")
    String username;

    @Size(min = 6, max = 100)
    @Schema(example = "new_password123")
    String password;

    @NotBlank
    @Size(max = 500)
    @Schema(example = "john_doe@example.com")
    String email;

    @Size(max = 500)
    @Schema(example = "wb_token_value")
    String wbToken;
}

