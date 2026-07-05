package com.cor.collectorservice.dto.card;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Размер товара")
public class SizeDto {
    @Schema(description = "ID характеристики размера", example = "123")
    private Long chrtID;

    @Schema(description = "Технический размер", example = "46")
    private String techSize;

    @Schema(description = "Размер Wildberries", example = "M")
    private String wbSize;

    @Schema(description = "SKU размеров")
    private List<String> skus;
}
