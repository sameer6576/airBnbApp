package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.BookingDto;
import com.sameerahmed.projects.airBnbApp.dto.HotelDto;
import com.sameerahmed.projects.airBnbApp.dto.HotelReportDto;
import com.sameerahmed.projects.airBnbApp.service.BookingService;
import com.sameerahmed.projects.airBnbApp.service.HotelService;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "Create a new hotel (inactive until activated)")
    public ResponseEntity<HotelDto> createNewHotel(@Valid @RequestBody HotelDto hotelDto) {
        log.info("Attempting to create a new hotel with name: {}", hotelDto.getName());
        HotelDto hotel = hotelService.createNewHotel(hotelDto);
        return new ResponseEntity<>(hotel, HttpStatus.CREATED);
    }

    @GetMapping("/{hotelId}")
    @Operation(summary = "Get hotel by id (owner only)")
    public ResponseEntity<HotelDto> getHotelById(@PathVariable Long hotelId) {
        return ResponseEntity.ok(hotelService.getHotelById(hotelId));
    }

    @PutMapping("/{hotelId}")
    @Operation(summary = "Update hotel details")
    public ResponseEntity<HotelDto> updateHotelById(@PathVariable Long hotelId,
                                                    @Valid @RequestBody HotelDto hotelDto) {
        return ResponseEntity.ok(hotelService.updateHotelById(hotelId, hotelDto));
    }

    @DeleteMapping("/{hotelId}")
    @Operation(summary = "Delete hotel and its rooms/inventory")
    public ResponseEntity<Void> deleteHotelById(@PathVariable Long hotelId) {
        hotelService.deleteHotelById(hotelId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{hotelId}/activate")
    @Operation(summary = "Activate hotel and generate one year of inventory")
    public ResponseEntity<Void> activateHotel(@PathVariable Long hotelId) {
        hotelService.activateHotel(hotelId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List hotels owned by the current manager")
    public ResponseEntity<List<HotelDto>> getAllHotels() {
        return ResponseEntity.ok(hotelService.getAllHotels());
    }

    @GetMapping("/{hotelId}/bookings")
    @Operation(summary = "List bookings for a hotel")
    public ResponseEntity<List<BookingDto>> getAllBookingsByHotelId(@PathVariable Long hotelId) {
        return ResponseEntity.ok(bookingService.getAllBookingsByHotelId(hotelId));
    }

    @GetMapping("/{hotelId}/reports")
    @Operation(summary = "Revenue report for a hotel over a date range")
    public ResponseEntity<HotelReportDto> getReportByHotelId(@PathVariable Long hotelId,
                                                             @RequestParam(required = false) LocalDate startDate,
                                                             @RequestParam(required = false) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().minusMonths(1);
        if (endDate == null) endDate = LocalDate.now();
        return ResponseEntity.ok(bookingService.getReportByHotelId(hotelId, startDate, endDate));
    }
}
