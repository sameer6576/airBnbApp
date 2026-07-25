package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.BookingDto;
import com.sameerahmed.projects.airBnbApp.dto.BookingRequest;
import com.sameerahmed.projects.airBnbApp.dto.GuestDto;
import com.sameerahmed.projects.airBnbApp.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookings")
public class HotelBookingController {

    private final BookingService bookingService;

    @PostMapping("/init")
    public ResponseEntity<BookingDto> initialiseBooking(@RequestBody BookingRequest bookingRequest) {
        return ResponseEntity.ok(bookingService.initialiseBooking(bookingRequest));
    }

    @PostMapping("/{bookingId}/addGuests")
    public ResponseEntity<BookingDto> addGuests(@PathVariable Long bookingId, @RequestBody List<GuestDto> guestDtos) {
        return ResponseEntity.ok(bookingService.addGuests(bookingId, guestDtos));
    }

    @PostMapping("/{bookingId}/payments")
    public ResponseEntity<Map<String, String>> initiatePayment(@PathVariable Long bookingId) {
        String sessionUrl = bookingService.initiatePayments(bookingId);
        return ResponseEntity.ok(Map.of("sessionUrl", sessionUrl));
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
