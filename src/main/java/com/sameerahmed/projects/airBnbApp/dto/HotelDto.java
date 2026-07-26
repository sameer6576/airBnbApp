package com.sameerahmed.projects.airBnbApp.dto;

import com.sameerahmed.projects.airBnbApp.entity.HotelContactInfo;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HotelDto {
    private Long id;

    @NotBlank
    private String name;

    @NotBlank
    private String city;

    private String[] photos;
    private String[] amenities;
    private HotelContactInfo contactInfo;
    private Boolean active;
    private Double averageRating;
    private Integer reviewCount;
}
