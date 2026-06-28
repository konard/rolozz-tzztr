package com.cor.collectorservice.dto.card;

import com.cor.collectorservice.util.FlexibleStringListDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacteristicDto {
    private Long id;
    private String name;
    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    private List<String> value;
}
