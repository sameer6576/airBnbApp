package com.sameerahmed.projects.airBnbApp.repository;

import com.sameerahmed.projects.airBnbApp.dto.BookingDto;
import com.sameerahmed.projects.airBnbApp.entity.Booking;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByPaymentSessionId(String sessionId);

    List<Booking> findByHotel(Hotel hotel);

    List<Booking> findByHotelAndCreatedAtBetween(Hotel hotel, LocalDateTime startDateTime, LocalDateTime endDateTime);

    List<Booking> findByUser(User user);
}