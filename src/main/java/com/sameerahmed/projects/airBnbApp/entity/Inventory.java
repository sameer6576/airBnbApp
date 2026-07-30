package com.sameerahmed.projects.airBnbApp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "inventory",
        uniqueConstraints = @UniqueConstraint(
                name = "unique_hotel_root_date",
                columnNames = {"hotel_id", "room_id", "date"}
        ),
        indexes = {
                // Search filters on city and date. The unique constraint above is
                // left-anchored on hotel_id, so Postgres cannot use it for that —
                // without this index the busiest endpoint is a sequential scan over
                // roughly 366 rows per room per year.
                @Index(name = "idx_inventory_city_date", columnList = "city, date"),
                // Every booking-path query is keyed on room plus a date range, and
                // Postgres does not index foreign keys automatically.
                @Index(name = "idx_inventory_room_date", columnList = "room_id, date")
        }
)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer bookedCount;

    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer reservedCount;

    @Column(nullable = false)
    private Integer totalCount;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal surgeFactor;

    // Matches Room.basePrice and Booking.amount. At precision 6 this capped a
    // nightly rate at 9999.99, so surge, occupancy, urgency and holiday
    // multipliers compounding over a base price could overflow the column and
    // abort the whole repricing batch for that hotel.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price; // basePrice * surgeFactor

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private boolean closed;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
