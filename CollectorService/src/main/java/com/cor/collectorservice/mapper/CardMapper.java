package com.cor.collectorservice.mapper;

import com.cor.collectorservice.dto.card.CardRequest;
import com.cor.collectorservice.dto.card.CardResponse;
import com.cor.collectorservice.dto.card.CharacteristicDto;
import com.cor.collectorservice.dto.card.DimensionsDto;
import com.cor.collectorservice.dto.card.PhotoDto;
import com.cor.collectorservice.dto.card.SizeDto;
import com.cor.collectorservice.entity.Card;
import com.cor.collectorservice.entity.Characteristic;
import com.cor.collectorservice.entity.Dimensions;
import com.cor.collectorservice.entity.Photo;
import com.cor.collectorservice.entity.Size;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CardMapper {

    Card toEntity(CardRequest cardRequest);

    CardResponse toResponse(Card card);

    DimensionsDto toDimensionsDto(Dimensions dimensions);

    PhotoDto toPhotoDto(Photo photo);

    CharacteristicDto toCharacteristicDto(Characteristic characteristic);

    SizeDto toSizeDto(Size size);

    List<PhotoDto> toPhotoDtoList(List<Photo> photos);

    List<CharacteristicDto> toCharacteristicDtoList(List<Characteristic> characteristics);

    List<SizeDto> toSizeDtoList(List<Size> sizes);
}
