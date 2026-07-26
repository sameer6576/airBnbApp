package com.sameerahmed.projects.airBnbApp.dto;

import com.sameerahmed.projects.airBnbApp.dto.validation.ValidDateRange;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@ValidDateRange(startField = "startDate", endField = "endDate")
public class HotelSearchRequest {
    @NotBlank
    private String city;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    @Positive
    private Integer roomsCount;

    @DecimalMin("0.0")
    private BigDecimal minPrice;

    @DecimalMin("0.0")
    private BigDecimal maxPrice;

    @Min(0)
    @Max(5)
    private Double minRating;

    @Positive
    private Integer minCapacity;

    private List<String> amenities = new ArrayList<>();

    private SearchSort sortBy = SearchSort.PRICE_ASC;

    @Min(0)
    private Integer page = 0;

    @Min(1)
    private Integer size = 10;

    public enum SearchSort {
        PRICE_ASC,
        PRICE_DESC,
        RATING_DESC
    }
}
