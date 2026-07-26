package com.sameerahmed.projects.airBnbApp.dto;

import com.sameerahmed.projects.airBnbApp.dto.validation.ValidDateRange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@ValidDateRange(startField = "startDate", endField = "endDate")
@Schema(description = "Bulk inventory update for a date range")
public class UpdateInventoryRequestDto {
    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    @Positive
    private BigDecimal surgeFactor;

    @NotNull
    private Boolean closed;
}
