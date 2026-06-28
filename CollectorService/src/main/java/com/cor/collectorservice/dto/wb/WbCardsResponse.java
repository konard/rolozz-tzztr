package com.cor.collectorservice.dto.wb;

import com.cor.collectorservice.dto.card.CardRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbCardsResponse {
    private List<CardRequest> cards;
    private Cursor cursor;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cursor {
        private String updatedAt;
        private Long nmID;
        private Integer total;
    }
}
