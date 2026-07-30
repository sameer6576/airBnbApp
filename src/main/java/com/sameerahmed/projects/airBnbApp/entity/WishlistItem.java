package com.sameerahmed.projects.airBnbApp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "wishlist_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "hotel_id"}),
        // The (user_id, hotel_id) unique constraint already serves lookups by user,
        // being left-anchored on it. Only the reverse direction needs its own index.
        indexes = @Index(name = "idx_wishlist_hotel", columnList = "hotel_id")
)
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
