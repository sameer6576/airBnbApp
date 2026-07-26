package com.sameerahmed.projects.airBnbApp.dto;

import com.sameerahmed.projects.airBnbApp.dto.validation.ValidDateRange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;

@Data
@ValidDateRange(startField = "startDate", endField = "endDate")
@Schema(description = "Hotel search criteria")
public class HotelSearchRequest {
    @NotBlank
    @Schema(example = "New York")
    private String city;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    @Positive
    @Schema(example = "1")
    private Integer roomsCount;

    @Min(0)
    private Integer page = 0;

    @Min(1)
    private Integer size = 10;
}
