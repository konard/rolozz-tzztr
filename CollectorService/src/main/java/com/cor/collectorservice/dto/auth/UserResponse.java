package com.cor.collectorservice.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Данные пользователя")
public class UserResponse {

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id;

    @Schema(example = "john_doe")
    String username;

    @Schema(example = "User")
    String role;

    @Schema(example = "2026-06-25T12:00:00")
    LocalDateTime createAt;

    @Schema(example = "2026-06-25T12:00:00")
    LocalDateTime updateAt;
}
