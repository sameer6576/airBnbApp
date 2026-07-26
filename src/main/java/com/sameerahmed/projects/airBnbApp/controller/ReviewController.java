package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.ReviewDto;
import com.sameerahmed.projects.airBnbApp.service.ReviewService;
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
    public ResponseEntity<ReviewDto> createReview(@PathVariable Long bookingId,
                                                  @Valid @RequestBody ReviewDto reviewDto) {
        return new ResponseEntity<>(reviewService.createReview(bookingId, reviewDto), HttpStatus.CREATED);
    }

    @GetMapping("/hotels/{hotelId}/reviews")
    public ResponseEntity<List<ReviewDto>> getHotelReviews(@PathVariable Long hotelId) {
        return ResponseEntity.ok(reviewService.getReviewsForHotel(hotelId));
    }

    @GetMapping("/users/myReviews")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ReviewDto>> getMyReviews() {
        return ResponseEntity.ok(reviewService.getMyReviews());
    }
}
