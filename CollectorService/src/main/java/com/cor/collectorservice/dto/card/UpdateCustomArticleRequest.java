package com.cor.collectorservice.dto.card;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCustomArticleRequest {
    @NotBlank(message = "Кастомный артикул не может быть пустым")
    private String customArticle;
}
