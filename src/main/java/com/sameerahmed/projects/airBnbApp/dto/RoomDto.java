package com.sameerahmed.projects.airBnbApp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RoomDto {
    private Long id;

    @NotBlank
    private String type;

    @NotNull
    @Positive
    private BigDecimal basePrice;

    private String[] photos;
    private String[] amenities;

    @NotNull
    @Positive
    private Integer totalCount;

    @NotNull
    @Positive
    private Integer capacity;
}
