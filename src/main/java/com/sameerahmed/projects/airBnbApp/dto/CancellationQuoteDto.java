package com.sameerahmed.projects.airBnbApp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CancellationQuoteDto {
    private boolean freeCancellation;
    private int daysUntilCheckIn;
    private int freeCancelDays;
    private BigDecimal refundPercent;
    private BigDecimal estimatedRefund;
}
