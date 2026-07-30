package com.sameerahmed.projects.airBnbApp.entity;

import com.sameerahmed.projects.airBnbApp.entity.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "booking",
        indexes = {
                // Looked up on every Stripe webhook delivery.
                @Index(name = "idx_booking_payment_session", columnList = "payment_session_id"),
                // The predicate both scheduled jobs run on.
                @Index(name = "idx_booking_status_hold", columnList = "booking_status, hold_expires_at"),
                // Foreign keys are not indexed automatically by Postgres, and both
                // of these back list endpoints: myBookings and bookings-by-hotel.
                @Index(name = "idx_booking_user", columnList = "user_id"),
                @Index(name = "idx_booking_hotel", columnList = "hotel_id")
        }
)
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer roomsCount;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus bookingStatus;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "booking_guest",
            joinColumns=@JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "guest_id")
    )
    private Set<Guest> guests;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    
    private String paymentSessionId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean expiryWarningSent = false;

    private LocalDateTime holdExpiresAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(unique = true)
    private String idempotencyKey; // userId:clientKey

    private String idempotencyFingerprint;
}
