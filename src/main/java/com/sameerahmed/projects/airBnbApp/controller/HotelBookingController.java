package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.*;
import com.sameerahmed.projects.airBnbApp.service.BookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookings")
@Tag(name = "Bookings")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class HotelBookingController {

    private final BookingService bookingService;

    @PostMapping("/init")
    public ResponseEntity<BookingDto> initialiseBooking(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BookingRequest bookingRequest) {
        return ResponseEntity.ok(bookingService.initialiseBooking(bookingRequest, idempotencyKey));
    }

    @PostMapping("/{bookingId}/addGuests")
    public ResponseEntity<BookingDto> addGuests(@PathVariable Long bookingId,
                                                @RequestBody List<@Valid GuestDto> guestDtos) {
        return ResponseEntity.ok(bookingService.addGuests(bookingId, guestDtos));
    }

    @PutMapping("/{bookingId}/guests")
    public ResponseEntity<BookingDto> replaceGuests(@PathVariable Long bookingId,
                                                    @RequestBody List<@Valid GuestDto> guestDtos) {
        return ResponseEntity.ok(bookingService.replaceGuests(bookingId, guestDtos));
    }

    @PatchMapping("/{bookingId}/dates")
    public ResponseEntity<BookingDto> modifyDates(@PathVariable Long bookingId,
                                                  @Valid @RequestBody ModifyBookingRequest request) {
        return ResponseEntity.ok(bookingService.modifyBookingDates(bookingId, request));
    }

    @PostMapping("/{bookingId}/payments")
    public ResponseEntity<Map<String, String>> initiatePayment(@PathVariable Long bookingId) {
        String sessionUrl = bookingService.initiatePayments(bookingId);
        return ResponseEntity.ok(Map.of("sessionUrl", sessionUrl));
    }

    @GetMapping("/{bookingId}/cancellation-quote")
    public ResponseEntity<CancellationQuoteDto> cancellationQuote(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.getCancellationQuote(bookingId));
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<Void> cancelPayment(@PathVariable Long bookingId) {
        bookingService.cancelPayment(bookingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{bookingId}/status")
    public ResponseEntity<Map<String, String>> getBookingStatus(@PathVariable Long bookingId) {
        return ResponseEntity.ok(Map.of("status", bookingService.getBookingStatus(bookingId)));
    }
}
