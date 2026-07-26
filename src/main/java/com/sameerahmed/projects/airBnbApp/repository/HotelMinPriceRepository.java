package com.sameerahmed.projects.airBnbApp.repository;

import com.sameerahmed.projects.airBnbApp.dto.HotelPriceDto;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.HotelMinPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface HotelMinPriceRepository extends JpaRepository<HotelMinPrice, Long> {

    @Query("""
            SELECT new com.sameerahmed.projects.airBnbApp.dto.HotelPriceDto(hmp.hotel, AVG(hmp.price))
            FROM HotelMinPrice hmp
            WHERE hmp.hotel.city = :city
                AND hmp.date BETWEEN :startDate AND :endDate
                AND hmp.hotel.active = true
                AND hmp.hotel IN (
                    SELECT i.hotel
                    FROM Inventory i
                    WHERE i.city = :city
                        AND i.date BETWEEN :startDate AND :endDate
                        AND i.closed = false
                        AND (i.totalCount - i.bookedCount - i.reservedCount) >= :roomsCount
                    GROUP BY i.hotel, i.room
                    HAVING COUNT(i.date) = :dateCount
                )
            GROUP BY hmp.hotel
            """)
    Page<HotelPriceDto> findHotelWithAvailableInventory(
            @Param("city") String city,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("roomsCount") Integer roomsCount,
            @Param("dateCount") Long dateCount,
            Pageable pageable
    );

    Optional<HotelMinPrice> findByHotelAndDate(Hotel hotel, LocalDate date);
}