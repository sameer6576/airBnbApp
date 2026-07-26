package com.sameerahmed.projects.airBnbApp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HotelAnalyticsDto {
    private Long hotelId;
    private String hotelName;
    private double occupancyPercent;
    private double cancellationRatePercent;
    private Long totalBookings;
    private Long confirmedBookings;
    private Long cancelledBookings;
    private BigDecimal totalRevenue;
    private List<RoomPerformanceDto> topRooms;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RoomPerformanceDto {
        private Long roomId;
        private String roomType;
        private Long confirmedBookings;
        private BigDecimal revenue;
    }
}
