package com.sameerahmed.projects.airBnbApp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Denormalised cheapest room price per hotel per day, driving search.
 *
 * The unique constraint is load-bearing rather than cosmetic. Search matches on
 * `HAVING COUNT(hmp.date) = :dateCount`, so a duplicated date inflates that
 * count, the HAVING fails, and the hotel silently vanishes from results — with
 * no error anywhere. Without the constraint, the non-atomic delete-then-reinsert
 * in HotelMinPriceServiceImpl can produce exactly that under concurrency.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "hotel_min_price",
        uniqueConstraints = @UniqueConstraint(
                name = "unique_hotel_min_price_date",
                columnNames = {"hotel_id", "date"}
        ),
        // Search groups by hotel over a date range; the unique constraint is
        // left-anchored on hotel_id and cannot serve a date-leading scan.
        indexes = @Index(name = "idx_hotel_min_price_date", columnList = "date, hotel_id")
)
public class HotelMinPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price; // cheapest room price on a particular day

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public HotelMinPrice(Hotel hotel, LocalDate date) {
        this.hotel = hotel;
        this.date = date;
    }
}
