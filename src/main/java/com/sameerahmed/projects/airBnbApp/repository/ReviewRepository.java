package com.sameerahmed.projects.airBnbApp.repository;

import com.sameerahmed.projects.airBnbApp.entity.Booking;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.Review;
import com.sameerahmed.projects.airBnbApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByHotelOrderByCreatedAtDesc(Hotel hotel);

    Optional<Review> findByBooking(Booking booking);

    boolean existsByBooking(Booking booking);

    @Query("SELECT COALESCE(AVG(r.rating), 0), COUNT(r) FROM Review r WHERE r.hotel.id = :hotelId")
    Object[] getAverageAndCount(@Param("hotelId") Long hotelId);

    List<Review> findByUser(User user);
}
