package com.cor.collectorservice.dto.card;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос на обновление кастомного артикула")
public class UpdateCustomArticleRequest {
    @NotBlank(message = "Кастомный артикул не может быть пустым")
    @Schema(description = "Кастомный артикул", example = "CUSTOM-001", required = true)
    private String customArticle;
}
