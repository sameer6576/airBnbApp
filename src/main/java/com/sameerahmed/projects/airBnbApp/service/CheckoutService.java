package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.entity.Booking;

public interface CheckoutService {

    String getCheckoutSession(Booking booking, String successUrl, String failureUrl);
}
