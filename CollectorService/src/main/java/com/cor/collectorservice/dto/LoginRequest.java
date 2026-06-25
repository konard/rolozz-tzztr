package com.cor.collectorservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Запрос на вход")
public class LoginRequest {

    @NotBlank
    @Schema(example = "john_doe")
    String username;

    @NotBlank
    @Schema(example = "password123")
    String password;
}
