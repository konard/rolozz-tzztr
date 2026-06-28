package com.cor.collectorservice.dto.card;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionsDto {
    private Integer width;
    private Integer height;
    private Integer length;
    private Double weightBrutto;
    private Boolean isValid;
}
