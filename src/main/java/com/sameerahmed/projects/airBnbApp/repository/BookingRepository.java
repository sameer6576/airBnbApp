package com.sameerahmed.projects.airBnbApp.repository;

import com.sameerahmed.projects.airBnbApp.entity.Booking;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.User;
import com.sameerahmed.projects.airBnbApp.entity.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Bookings whose hold has lapsed, including rows where {@code holdExpiresAt} was
     * never set — a derived {@code ...HoldExpiresAtBefore} query cannot match null,
     * so those rows held their reserved inventory forever.
     */
    @Query("""
                    SELECT b FROM Booking b
                    WHERE b.bookingStatus IN :statuses
                        AND (
                            (b.holdExpiresAt IS NOT NULL AND b.holdExpiresAt < :now)
                            OR (b.holdExpiresAt IS NULL AND b.createdAt IS NOT NULL AND b.createdAt < :fallbackCutoff)
                        )
            """)
    List<Booking> findExpiredHolds(@Param("statuses") Collection<BookingStatus> statuses,
                                   @Param("now") LocalDateTime now,
                                   @Param("fallbackCutoff") LocalDateTime fallbackCutoff);

    List<Booking> findByBookingStatusInAndExpiryWarningSentFalseAndHoldExpiresAtBetween(
            Collection<BookingStatus> statuses,
            LocalDateTime start,
            LocalDateTime end
    );

    boolean existsByHotelAndBookingStatusIn(Hotel hotel, Collection<BookingStatus> statuses);

    void deleteByHotel(Hotel hotel);
}
