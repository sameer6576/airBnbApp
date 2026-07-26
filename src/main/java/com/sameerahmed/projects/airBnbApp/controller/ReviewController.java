package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.ReviewDto;
import com.sameerahmed.projects.airBnbApp.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/bookings/{bookingId}/reviews")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a review after check-out for a confirmed booking")
    public ResponseEntity<ReviewDto> createReview(@PathVariable Long bookingId,
                                                  @Valid @RequestBody ReviewDto reviewDto) {
        return new ResponseEntity<>(reviewService.createReview(bookingId, reviewDto), HttpStatus.CREATED);
    }

    @GetMapping("/hotels/{hotelId}/reviews")
    @Operation(summary = "List reviews for a hotel")
    public ResponseEntity<List<ReviewDto>> getHotelReviews(@PathVariable Long hotelId) {
        return ResponseEntity.ok(reviewService.getReviewsForHotel(hotelId));
    }

    @GetMapping("/users/myReviews")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List reviews written by the current user")
    public ResponseEntity<List<ReviewDto>> getMyReviews() {
        return ResponseEntity.ok(reviewService.getMyReviews());
    }
}
