package com.cor.collectorservice.entity;

import lombok.Data;

import java.util.List;

@Data
public class Characteristic {
    private Long id;
    private String name;
    private List<String> value;
}
