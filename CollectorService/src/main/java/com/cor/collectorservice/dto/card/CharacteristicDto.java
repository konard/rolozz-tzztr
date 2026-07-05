package com.cor.collectorservice.dto.card;

import com.cor.collectorservice.util.FlexibleStringListDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
@Schema(description = "Характеристика товара")
public class CharacteristicDto {
    @Schema(description = "ID характеристики", example = "123")
    private Long id;

    @Schema(description = "Название характеристики", example = "Цвет")
    private String name;

    @Schema(description = "Значения характеристики")
    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    private List<String> value;
}
