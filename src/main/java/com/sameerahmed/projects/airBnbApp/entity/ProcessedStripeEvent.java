package com.sameerahmed.projects.airBnbApp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One row per Stripe event we have handled, keyed on Stripe's own event id.
 *
 * Stripe guarantees at-least-once delivery, so the same event can arrive more
 * than once — and concurrent deliveries of a duplicate both read the booking as
 * PAYMENT_PENDING under READ COMMITTED, so a status check alone does not
 * deduplicate. The primary key is what actually enforces it: the second writer
 * fails on the constraint, its transaction rolls back, and the retry then sees
 * the committed row and skips.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "processed_stripe_event")
public class ProcessedStripeEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(nullable = false)
    private String eventType;

    @CreationTimestamp
    private LocalDateTime processedAt;

    public ProcessedStripeEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }
}
