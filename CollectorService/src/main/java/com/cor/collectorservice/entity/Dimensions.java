package com.cor.collectorservice.entity;

import lombok.Data;

@Data
public class Dimensions {
    private Integer width;
    private Integer height;
    private Integer length;
    private Double weightBrutto;
    private Boolean isValid;
}
