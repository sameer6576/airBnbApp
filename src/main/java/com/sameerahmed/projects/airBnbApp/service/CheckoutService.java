package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.entity.Booking;

public interface CheckoutService {

    /**
     * Creates a Stripe Checkout Session for the booking and returns its id and URL.
     *
     * <p>Returning both rather than persisting the id here keeps the write inside
     * the caller's transaction, so a rollback cannot leave a session recorded
     * against a booking that was never updated.
     */
    CheckoutSession getCheckoutSession(Booking booking, String successUrl, String failureUrl);

    record CheckoutSession(String id, String url) {
    }
}
