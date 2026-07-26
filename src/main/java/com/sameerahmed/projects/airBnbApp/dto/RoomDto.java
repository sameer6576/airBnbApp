package com.sameerahmed.projects.airBnbApp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Room details for admin create/update")
public class RoomDto {
    private Long id;

    @NotBlank
    @Schema(example = "Deluxe King")
    private String type;

    @NotNull
    @Positive
    @Schema(example = "150.00")
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
