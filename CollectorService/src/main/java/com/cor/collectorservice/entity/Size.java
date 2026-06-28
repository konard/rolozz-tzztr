package com.cor.collectorservice.entity;

import lombok.Data;

import java.util.List;

@Data
public class Size {
    private Long chrtID;
    private String techSize;
    private String wbSize;
    private List<String> skus;
}
