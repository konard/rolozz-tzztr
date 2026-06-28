package com.cor.collectorservice.dto.card;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SizeDto {
    private Long chrtID;
    private String techSize;
    private String wbSize;
    private List<String> skus;
}
