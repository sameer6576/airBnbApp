package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.ReviewDto;

import java.util.List;

public interface ReviewService {
    ReviewDto createReview(Long bookingId, ReviewDto reviewDto);

    List<ReviewDto> getReviewsForHotel(Long hotelId);

    List<ReviewDto> getMyReviews();
}
