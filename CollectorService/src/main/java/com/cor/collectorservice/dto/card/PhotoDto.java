package com.cor.collectorservice.dto.card;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Фотографии товара")
public class PhotoDto {
    @Schema(description = "Большое фото", example = "https://photo.url/big.jpg")
    private String big;

    @Schema(description = "Фото 246x328", example = "https://photo.url/246x328.jpg")
    private String c246x328;

    @Schema(description = "Фото 516x688", example = "https://photo.url/516x688.jpg")
    private String c516x688;

    @Schema(description = "HQ фото", example = "https://photo.url/hq.jpg")
    private String hq;

    @Schema(description = "Квадратное фото", example = "https://photo.url/square.jpg")
    private String square;

    @Schema(description = "Миниатюра", example = "https://photo.url/tm.jpg")
    private String tm;
}
