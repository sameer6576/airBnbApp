package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.BookingDto;
import com.sameerahmed.projects.airBnbApp.dto.BookingRequest;
import com.sameerahmed.projects.airBnbApp.dto.GuestDto;
import com.sameerahmed.projects.airBnbApp.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookings")
@Tag(name = "Bookings")
@SecurityRequirement(name = "bearerAuth")
public class HotelBookingController {

    private final BookingService bookingService;

    @PostMapping("/init")
    @Operation(summary = "Initialize a booking and reserve inventory")
    public ResponseEntity<BookingDto> initialiseBooking(@Valid @RequestBody BookingRequest bookingRequest) {
        return ResponseEntity.ok(bookingService.initialiseBooking(bookingRequest));
    }

    @PostMapping("/{bookingId}/addGuests")
    @Operation(summary = "Add guests to a reserved booking (by id or inline details)")
    public ResponseEntity<BookingDto> addGuests(@PathVariable Long bookingId, @RequestBody List<GuestDto> guestDtos) {
        return ResponseEntity.ok(bookingService.addGuests(bookingId, guestDtos));
    }

    @PostMapping("/{bookingId}/payments")
    @Operation(summary = "Create a Stripe Checkout session for the booking")
    public ResponseEntity<Map<String, String>> initiatePayment(@PathVariable Long bookingId) {
        String sessionUrl = bookingService.initiatePayments(bookingId);
        return ResponseEntity.ok(Map.of("sessionUrl", sessionUrl));
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel a confirmed booking and refund")
    public ResponseEntity<Void> cancelPayment(@PathVariable Long bookingId) {
        bookingService.cancelPayment(bookingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{bookingId}/status")
    @Operation(summary = "Get booking status")
    public ResponseEntity<Map<String, String>> getBookingStatus(@PathVariable Long bookingId) {
        return ResponseEntity.ok(Map.of("status", bookingService.getBookingStatus(bookingId)));
    }
}
