package com.cor.collectorservice.dto.card;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardResponse {
    private Long nmID;
    private String customArticle;
    private Long imtID;
    private String nmUUID;
    private Integer subjectID;
    private String subjectName;
    private String vendorCode;
    private String brand;
    private String title;
    private String description;
    private String video;
    private Boolean needKiz;
    private Boolean kizMarked;
    private DimensionsDto dimensions;
    private List<PhotoDto> photos;
    private List<CharacteristicDto> characteristics;
    private List<SizeDto> sizes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
