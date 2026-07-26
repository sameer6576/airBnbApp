package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.BookingDto;
import com.sameerahmed.projects.airBnbApp.dto.HotelAnalyticsDto;
import com.sameerahmed.projects.airBnbApp.dto.HotelDto;
import com.sameerahmed.projects.airBnbApp.dto.HotelReportDto;
import com.sameerahmed.projects.airBnbApp.service.BookingService;
import com.sameerahmed.projects.airBnbApp.service.HotelService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/hotels")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Hotels")
@SecurityRequirement(name = "bearerAuth")
public class HotelController {
    private final HotelService hotelService;
    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<HotelDto> createNewHotel(@Valid @RequestBody HotelDto hotelDto) {
        log.info("Attempting to create a new hotel with name: {}", hotelDto.getName());
        HotelDto hotel = hotelService.createNewHotel(hotelDto);
        return new ResponseEntity<>(hotel, HttpStatus.CREATED);
    }

    @GetMapping("/{hotelId}")
    public ResponseEntity<HotelDto> getHotelById(@PathVariable Long hotelId) {
        return ResponseEntity.ok(hotelService.getHotelById(hotelId));
    }

    @PutMapping("/{hotelId}")
    public ResponseEntity<HotelDto> updateHotelById(@PathVariable Long hotelId,
                                                    @Valid @RequestBody HotelDto hotelDto) {
        return ResponseEntity.ok(hotelService.updateHotelById(hotelId, hotelDto));
    }

    @DeleteMapping("/{hotelId}")
    public ResponseEntity<Void> deleteHotelById(@PathVariable Long hotelId) {
        hotelService.deleteHotelById(hotelId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{hotelId}/activate")
    public ResponseEntity<Void> activateHotel(@PathVariable Long hotelId) {
        hotelService.activateHotel(hotelId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<HotelDto>> getAllHotels() {
        return ResponseEntity.ok(hotelService.getAllHotels());
    }

    @GetMapping("/{hotelId}/bookings")
    public ResponseEntity<List<BookingDto>> getAllBookingsByHotelId(@PathVariable Long hotelId) {
        return ResponseEntity.ok(bookingService.getAllBookingsByHotelId(hotelId));
    }

    @GetMapping("/{hotelId}/reports")
    public ResponseEntity<HotelReportDto> getReportByHotelId(@PathVariable Long hotelId,
                                                             @RequestParam(required = false) LocalDate startDate,
                                                             @RequestParam(required = false) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().minusMonths(1);
        if (endDate == null) endDate = LocalDate.now();
        return ResponseEntity.ok(bookingService.getReportByHotelId(hotelId, startDate, endDate));
    }

    @GetMapping("/{hotelId}/analytics")
    public ResponseEntity<HotelAnalyticsDto> getAnalytics(@PathVariable Long hotelId,
                                                          @RequestParam(required = false) LocalDate startDate,
                                                          @RequestParam(required = false) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().minusMonths(1);
        if (endDate == null) endDate = LocalDate.now();
        return ResponseEntity.ok(bookingService.getHotelAnalytics(hotelId, startDate, endDate));
    }
}
