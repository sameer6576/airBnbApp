package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.entity.Booking;

public interface NotificationService {
    void sendBookingConfirmed(Booking booking);

    void sendBookingCancelled(Booking booking, java.math.BigDecimal refundAmount);

    void sendBookingExpiryWarning(Booking booking);
}
