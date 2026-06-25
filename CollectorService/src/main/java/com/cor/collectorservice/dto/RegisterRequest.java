package com.cor.collectorservice.dto;

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
@Schema(description = "Запрос на регистрацию")
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Schema(example = "john_doe")
    String username;

    @NotBlank
    @Size(min = 6, max = 100)
    @Schema(example = "password123")
    String password;
}
