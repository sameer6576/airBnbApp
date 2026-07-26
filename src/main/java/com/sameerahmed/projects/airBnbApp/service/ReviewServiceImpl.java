package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.ReviewDto;
import com.sameerahmed.projects.airBnbApp.entity.Booking;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.Review;
import com.sameerahmed.projects.airBnbApp.entity.User;
import com.sameerahmed.projects.airBnbApp.entity.enums.BookingStatus;
import com.sameerahmed.projects.airBnbApp.exception.ResourceNotFoundException;
import com.sameerahmed.projects.airBnbApp.repository.BookingRepository;
import com.sameerahmed.projects.airBnbApp.repository.HotelRepository;
import com.sameerahmed.projects.airBnbApp.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.sameerahmed.projects.airBnbApp.util.AppUtils.getCurrentUser;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final HotelRepository hotelRepository;

    @Override
    @Transactional
    public ReviewDto createReview(Long bookingId, ReviewDto reviewDto) {
        User user = getCurrentUser();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!Objects.equals(user.getId(), booking.getUser().getId())) {
            throw new AccessDeniedException("Booking does not belong to this user");
        }
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be reviewed");
        }
        if (booking.getCheckOutDate().isAfter(LocalDate.now())) {
            throw new IllegalStateException("Reviews are allowed only after check-out date");
        }
        if (reviewRepository.existsByBooking(booking)) {
            throw new IllegalStateException("A review already exists for this booking");
        }

        Review review = new Review();
        review.setBooking(booking);
        review.setHotel(booking.getHotel());
        review.setUser(user);
        review.setRating(reviewDto.getRating());
        review.setComment(reviewDto.getComment());
        review = reviewRepository.save(review);

        refreshHotelRating(booking.getHotel().getId());
        return toDto(review);
    }

    @Override
    public List<ReviewDto> getReviewsForHotel(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with id: " + hotelId));
        return reviewRepository.findByHotelOrderByCreatedAtDesc(hotel).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<ReviewDto> getMyReviews() {
        return reviewRepository.findByUser(getCurrentUser()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private void refreshHotelRating(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with id: " + hotelId));
        List<Review> reviews = reviewRepository.findByHotelOrderByCreatedAtDesc(hotel);
        if (reviews.isEmpty()) {
            hotel.setAverageRating(0.0);
            hotel.setReviewCount(0);
        } else {
            double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
            hotel.setAverageRating(Math.round(avg * 10.0) / 10.0);
            hotel.setReviewCount(reviews.size());
        }
        hotelRepository.save(hotel);
    }

    private ReviewDto toDto(Review review) {
        ReviewDto dto = new ReviewDto();
        dto.setId(review.getId());
        dto.setHotelId(review.getHotel().getId());
        dto.setBookingId(review.getBooking().getId());
        dto.setUserName(review.getUser().getName() != null ? review.getUser().getName() : review.getUser().getEmail());
        dto.setRating(review.getRating());
        dto.setComment(review.getComment());
        dto.setCreatedAt(review.getCreatedAt());
        return dto;
    }
}
