package com.sameerahmed.projects.airBnbApp.repository;

import com.sameerahmed.projects.airBnbApp.dto.HotelPriceDto;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.HotelMinPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HotelMinPriceRepository extends JpaRepository<HotelMinPrice, Long> {

    /**
     * A stay range, so the check-out day is excluded and {@code nightCount} is the
     * number of nights rather than the number of calendar days. Both HAVING
     * clauses compare against it, which is what requires availability and a price
     * on every night rather than merely on some of them.
     */
    @Query("""
            SELECT new com.sameerahmed.projects.airBnbApp.dto.HotelPriceDto(
                hmp.hotel,
                AVG(hmp.price)
            )
            FROM HotelMinPrice hmp
            WHERE hmp.hotel.city = :city
              AND hmp.hotel.active = true
              AND hmp.date >= :checkInDate
              AND hmp.date < :checkOutDate
              AND (:minRating IS NULL OR COALESCE(hmp.hotel.averageRating,0) >= :minRating)
            
              AND hmp.hotel IN (
            
                  SELECT i.hotel
                  FROM Inventory i
                  WHERE i.city = :city
                    AND i.date >= :checkInDate
                    AND i.date < :checkOutDate
                    AND i.closed = false
                    AND (i.totalCount - i.bookedCount - i.reservedCount) >= :roomsCount
                    AND (:minCapacity IS NULL OR i.room.capacity >= :minCapacity)
            
                  GROUP BY i.hotel
                  HAVING COUNT(DISTINCT i.date) = :nightCount
            
              )
            
            GROUP BY hmp.hotel
            
            HAVING COUNT(hmp.date) = :nightCount
               AND (:minPrice IS NULL OR AVG(hmp.price) >= :minPrice)
               AND (:maxPrice IS NULL OR AVG(hmp.price) <= :maxPrice)
            """)
    List<HotelPriceDto> findHotelWithAvailableInventory(
            @Param("city") String city,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate,
            @Param("roomsCount") Integer roomsCount,
            @Param("nightCount") Long nightCount,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("minRating") Double minRating,
            @Param("minCapacity") Integer minCapacity
    );

    void deleteByHotel(Hotel hotel);
}
