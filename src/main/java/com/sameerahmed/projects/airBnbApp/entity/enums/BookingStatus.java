package com.sameerahmed.projects.airBnbApp.entity.enums;

public enum BookingStatus {
    RESERVED,
    GUEST_ADDED,
    PAYMENT_PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED,

    /**
     * Payment was captured but the rooms could not be held — the hold had already
     * expired and the inventory was gone by the time the webhook arrived. The
     * money has been returned. Distinct from CANCELLED, which is guest-initiated.
     */
    REFUNDED
}
