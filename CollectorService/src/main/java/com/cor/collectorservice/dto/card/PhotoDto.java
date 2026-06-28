package com.cor.collectorservice.dto.card;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoDto {
    private String big;
    private String c246x328;
    private String c516x688;
    private String hq;
    private String square;
    private String tm;
}
