package com.sameerahmed.projects.airBnbApp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HotelPriceDto {
    private HotelDto hotel;
    private Double price;
    private Double averageRating;

    public HotelPriceDto(com.sameerahmed.projects.airBnbApp.entity.Hotel hotel, Double price) {
        this.hotel = mapHotel(hotel);
        this.price = price;
        this.averageRating = hotel.getAverageRating() != null ? hotel.getAverageRating() : 0.0;
    }

    public HotelPriceDto(com.sameerahmed.projects.airBnbApp.entity.Hotel hotel, Double price, Double averageRating) {
        this.hotel = mapHotel(hotel);
        this.price = price;
        this.averageRating = averageRating != null ? averageRating : 0.0;
    }

    private static HotelDto mapHotel(com.sameerahmed.projects.airBnbApp.entity.Hotel hotel) {
        HotelDto dto = new HotelDto();
        dto.setId(hotel.getId());
        dto.setName(hotel.getName());
        dto.setCity(hotel.getCity());
        dto.setPhotos(hotel.getPhotos());
        dto.setAmenities(hotel.getAmenities());
        dto.setContactInfo(hotel.getContactInfo());
        dto.setActive(hotel.getActive());
        dto.setAverageRating(hotel.getAverageRating());
        dto.setReviewCount(hotel.getReviewCount());
        return dto;
    }
}
