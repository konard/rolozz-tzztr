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
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-28T22:21:29+0300",
    comments = "version: 1.5.5.Final, compiler: IncrementalProcessingEnvironment from gradle-language-java-8.14.5.jar, environment: Java 21.0.4 (Oracle Corporation)"
)
@Component
public class CardMapperImpl implements CardMapper {

    @Override
    public Card toEntity(CardRequest cardRequest) {
        if ( cardRequest == null ) {
            return null;
        }

        Card card = new Card();

        card.setNmID( cardRequest.getNmID() );
        card.setImtID( cardRequest.getImtID() );
        card.setNmUUID( cardRequest.getNmUUID() );
        card.setSubjectID( cardRequest.getSubjectID() );
        card.setSubjectName( cardRequest.getSubjectName() );
        card.setVendorCode( cardRequest.getVendorCode() );
        card.setBrand( cardRequest.getBrand() );
        card.setTitle( cardRequest.getTitle() );
        card.setDescription( cardRequest.getDescription() );
        card.setVideo( cardRequest.getVideo() );
        card.setNeedKiz( cardRequest.getNeedKiz() );
        card.setKizMarked( cardRequest.getKizMarked() );
        card.setDimensions( dimensionsDtoToDimensions( cardRequest.getDimensions() ) );
        card.setPhotos( photoDtoListToPhotoList( cardRequest.getPhotos() ) );
        card.setCharacteristics( characteristicDtoListToCharacteristicList( cardRequest.getCharacteristics() ) );
        card.setSizes( sizeDtoListToSizeList( cardRequest.getSizes() ) );
        card.setCreatedAt( cardRequest.getCreatedAt() );
        card.setUpdatedAt( cardRequest.getUpdatedAt() );

        return card;
    }

    @Override
    public CardResponse toResponse(Card card) {
        if ( card == null ) {
            return null;
        }

        CardResponse.CardResponseBuilder cardResponse = CardResponse.builder();

        cardResponse.nmID( card.getNmID() );
        cardResponse.customArticle( card.getCustomArticle() );
        cardResponse.imtID( card.getImtID() );
        cardResponse.nmUUID( card.getNmUUID() );
        cardResponse.subjectID( card.getSubjectID() );
        cardResponse.subjectName( card.getSubjectName() );
        cardResponse.vendorCode( card.getVendorCode() );
        cardResponse.brand( card.getBrand() );
        cardResponse.title( card.getTitle() );
        cardResponse.description( card.getDescription() );
        cardResponse.video( card.getVideo() );
        cardResponse.needKiz( card.getNeedKiz() );
        cardResponse.kizMarked( card.getKizMarked() );
        cardResponse.dimensions( toDimensionsDto( card.getDimensions() ) );
        cardResponse.photos( toPhotoDtoList( card.getPhotos() ) );
        cardResponse.characteristics( toCharacteristicDtoList( card.getCharacteristics() ) );
        cardResponse.sizes( toSizeDtoList( card.getSizes() ) );
        cardResponse.createdAt( card.getCreatedAt() );
        cardResponse.updatedAt( card.getUpdatedAt() );

        return cardResponse.build();
    }

    @Override
    public DimensionsDto toDimensionsDto(Dimensions dimensions) {
        if ( dimensions == null ) {
            return null;
        }

        DimensionsDto.DimensionsDtoBuilder dimensionsDto = DimensionsDto.builder();

        dimensionsDto.width( dimensions.getWidth() );
        dimensionsDto.height( dimensions.getHeight() );
        dimensionsDto.length( dimensions.getLength() );
        dimensionsDto.weightBrutto( dimensions.getWeightBrutto() );
        dimensionsDto.isValid( dimensions.getIsValid() );

        return dimensionsDto.build();
    }

    @Override
    public PhotoDto toPhotoDto(Photo photo) {
        if ( photo == null ) {
            return null;
        }

        PhotoDto.PhotoDtoBuilder photoDto = PhotoDto.builder();

        photoDto.big( photo.getBig() );
        photoDto.c246x328( photo.getC246x328() );
        photoDto.c516x688( photo.getC516x688() );
        photoDto.hq( photo.getHq() );
        photoDto.square( photo.getSquare() );
        photoDto.tm( photo.getTm() );

        return photoDto.build();
    }

    @Override
    public CharacteristicDto toCharacteristicDto(Characteristic characteristic) {
        if ( characteristic == null ) {
            return null;
        }

        CharacteristicDto.CharacteristicDtoBuilder characteristicDto = CharacteristicDto.builder();

        characteristicDto.id( characteristic.getId() );
        characteristicDto.name( characteristic.getName() );
        List<String> list = characteristic.getValue();
        if ( list != null ) {
            characteristicDto.value( new ArrayList<String>( list ) );
        }

        return characteristicDto.build();
    }

    @Override
    public SizeDto toSizeDto(Size size) {
        if ( size == null ) {
            return null;
        }

        SizeDto.SizeDtoBuilder sizeDto = SizeDto.builder();

        sizeDto.chrtID( size.getChrtID() );
        sizeDto.techSize( size.getTechSize() );
        sizeDto.wbSize( size.getWbSize() );
        List<String> list = size.getSkus();
        if ( list != null ) {
            sizeDto.skus( new ArrayList<String>( list ) );
        }

        return sizeDto.build();
    }

    @Override
    public List<PhotoDto> toPhotoDtoList(List<Photo> photos) {
        if ( photos == null ) {
            return null;
        }

        List<PhotoDto> list = new ArrayList<PhotoDto>( photos.size() );
        for ( Photo photo : photos ) {
            list.add( toPhotoDto( photo ) );
        }

        return list;
    }

    @Override
    public List<CharacteristicDto> toCharacteristicDtoList(List<Characteristic> characteristics) {
        if ( characteristics == null ) {
            return null;
        }

        List<CharacteristicDto> list = new ArrayList<CharacteristicDto>( characteristics.size() );
        for ( Characteristic characteristic : characteristics ) {
            list.add( toCharacteristicDto( characteristic ) );
        }

        return list;
    }

    @Override
    public List<SizeDto> toSizeDtoList(List<Size> sizes) {
        if ( sizes == null ) {
            return null;
        }

        List<SizeDto> list = new ArrayList<SizeDto>( sizes.size() );
        for ( Size size : sizes ) {
            list.add( toSizeDto( size ) );
        }

        return list;
    }

    protected Dimensions dimensionsDtoToDimensions(DimensionsDto dimensionsDto) {
        if ( dimensionsDto == null ) {
            return null;
        }

        Dimensions dimensions = new Dimensions();

        dimensions.setWidth( dimensionsDto.getWidth() );
        dimensions.setHeight( dimensionsDto.getHeight() );
        dimensions.setLength( dimensionsDto.getLength() );
        dimensions.setWeightBrutto( dimensionsDto.getWeightBrutto() );
        dimensions.setIsValid( dimensionsDto.getIsValid() );

        return dimensions;
    }

    protected Photo photoDtoToPhoto(PhotoDto photoDto) {
        if ( photoDto == null ) {
            return null;
        }

        Photo photo = new Photo();

        photo.setBig( photoDto.getBig() );
        photo.setC246x328( photoDto.getC246x328() );
        photo.setC516x688( photoDto.getC516x688() );
        photo.setHq( photoDto.getHq() );
        photo.setSquare( photoDto.getSquare() );
        photo.setTm( photoDto.getTm() );

        return photo;
    }

    protected List<Photo> photoDtoListToPhotoList(List<PhotoDto> list) {
        if ( list == null ) {
            return null;
        }

        List<Photo> list1 = new ArrayList<Photo>( list.size() );
        for ( PhotoDto photoDto : list ) {
            list1.add( photoDtoToPhoto( photoDto ) );
        }

        return list1;
    }

    protected Characteristic characteristicDtoToCharacteristic(CharacteristicDto characteristicDto) {
        if ( characteristicDto == null ) {
            return null;
        }

        Characteristic characteristic = new Characteristic();

        characteristic.setId( characteristicDto.getId() );
        characteristic.setName( characteristicDto.getName() );
        List<String> list = characteristicDto.getValue();
        if ( list != null ) {
            characteristic.setValue( new ArrayList<String>( list ) );
        }

        return characteristic;
    }

    protected List<Characteristic> characteristicDtoListToCharacteristicList(List<CharacteristicDto> list) {
        if ( list == null ) {
            return null;
        }

        List<Characteristic> list1 = new ArrayList<Characteristic>( list.size() );
        for ( CharacteristicDto characteristicDto : list ) {
            list1.add( characteristicDtoToCharacteristic( characteristicDto ) );
        }

        return list1;
    }

    protected Size sizeDtoToSize(SizeDto sizeDto) {
        if ( sizeDto == null ) {
            return null;
        }

        Size size = new Size();

        size.setChrtID( sizeDto.getChrtID() );
        size.setTechSize( sizeDto.getTechSize() );
        size.setWbSize( sizeDto.getWbSize() );
        List<String> list = sizeDto.getSkus();
        if ( list != null ) {
            size.setSkus( new ArrayList<String>( list ) );
        }

        return size;
    }

    protected List<Size> sizeDtoListToSizeList(List<SizeDto> list) {
        if ( list == null ) {
            return null;
        }

        List<Size> list1 = new ArrayList<Size>( list.size() );
        for ( SizeDto sizeDto : list ) {
            list1.add( sizeDtoToSize( sizeDto ) );
        }

        return list1;
    }
}
