package com.sameerahmed.projects.airBnbApp.repository;

import com.sameerahmed.projects.airBnbApp.entity.Booking;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.User;
import com.sameerahmed.projects.airBnbApp.entity.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByPaymentSessionId(String sessionId);

    List<Booking> findByHotel(Hotel hotel);

    List<Booking> findByHotelAndCreatedAtBetween(Hotel hotel, LocalDateTime startDateTime, LocalDateTime endDateTime);

    List<Booking> findByUser(User user);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    List<Booking> findByBookingStatusInAndHoldExpiresAtBefore(Collection<BookingStatus> statuses, LocalDateTime holdExpiresAt);

    List<Booking> findByBookingStatusInAndExpiryWarningSentFalseAndHoldExpiresAtBetween(
            Collection<BookingStatus> statuses,
            LocalDateTime start,
            LocalDateTime end
    );
}
