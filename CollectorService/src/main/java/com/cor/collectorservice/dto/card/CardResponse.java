package com.cor.collectorservice.dto.card;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Данные карточки товара")
public class CardResponse {
    @Schema(description = "ID карточки в Wildberries", example = "12345678")
    private Long nmID;

    @Schema(description = "Кастомный артикул", example = "CUSTOM-001")
    private String customArticle;

    @Schema(description = "ID категории", example = "12345")
    private Long imtID;

    @Schema(description = "UUID карточки", example = "550e8400-e29b-41d4-a716-446655440000")
    private String nmUUID;

    @Schema(description = "ID предмета", example = "123")
    private Integer subjectID;

    @Schema(description = "Название предмета", example = "Одежда")
    private String subjectName;

    @Schema(description = "Артикул продавца", example = "ART-001")
    private String vendorCode;

    @Schema(description = "Бренд", example = "Nike")
    private String brand;

    @Schema(description = "Название товара", example = "Футболка")
    private String title;

    @Schema(description = "Описание товара", example = "Хлопковая футболка")
    private String description;

    @Schema(description = "Ссылка на видео", example = "https://youtube.com/watch?v=xxx")
    private String video;

    @Schema(description = "Нужен ли КИЗ", example = "true")
    private Boolean needKiz;

    @Schema(description = "КИЗ отмечен", example = "false")
    private Boolean kizMarked;

    @Schema(description = "Габариты")
    private DimensionsDto dimensions;

    @Schema(description = "Фотографии")
    private List<PhotoDto> photos;

    @Schema(description = "Характеристики")
    private List<CharacteristicDto> characteristics;

    @Schema(description = "Размеры")
    private List<SizeDto> sizes;

    @Schema(description = "Дата создания в БД", example = "2026-06-25T12:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Дата обновления в БД", example = "2026-06-25T12:00:00")
    private LocalDateTime updatedAt;
}
