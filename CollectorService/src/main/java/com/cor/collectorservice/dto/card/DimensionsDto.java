package com.cor.collectorservice.dto.card;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Габариты товара")
public class DimensionsDto {
    @Schema(description = "Ширина (мм)", example = "200")
    private Integer width;

    @Schema(description = "Высота (мм)", example = "100")
    private Integer height;

    @Schema(description = "Длина (мм)", example = "50")
    private Integer length;

    @Schema(description = "Вес брутто (кг)", example = "0.5")
    private Double weightBrutto;

    @Schema(description = "Валидность габаритов", example = "true")
    private Boolean isValid;
}
