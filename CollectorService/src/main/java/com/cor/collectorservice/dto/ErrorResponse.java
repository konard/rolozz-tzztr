package com.cor.collectorservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Ответ с ошибкой")
public class ErrorResponse {

    @Schema(example = "2026-06-25T12:00:00")
    LocalDateTime timestamp;

    @Schema(example = "400")
    int status;

    @Schema(example = "Bad Request")
    String error;

    @Schema(example = "Username already exists")
    String message;

    @Schema(example = "/api/auth/register")
    String path;
}
