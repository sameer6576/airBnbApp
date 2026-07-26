package com.sameerahmed.projects.airBnbApp.dto;

import com.sameerahmed.projects.airBnbApp.dto.validation.ValidDateRange;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Hotel search criteria with optional filters and sort")
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

    @DecimalMin("0.0")
    @Schema(example = "50.00", description = "Minimum average nightly price")
    private BigDecimal minPrice;

    @DecimalMin("0.0")
    @Schema(example = "500.00", description = "Maximum average nightly price")
    private BigDecimal maxPrice;

    @Min(0)
    @Max(5)
    @Schema(example = "3.5", description = "Minimum hotel average rating")
    private Double minRating;

    @Positive
    @Schema(example = "2", description = "Minimum room capacity (guests)")
    private Integer minCapacity;

    @Schema(description = "Hotel must include all of these amenities")
    private List<String> amenities = new ArrayList<>();

    @Schema(example = "PRICE_ASC", allowableValues = {"PRICE_ASC", "PRICE_DESC", "RATING_DESC"})
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
