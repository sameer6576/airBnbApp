package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.entity.Booking;
import com.sameerahmed.projects.airBnbApp.entity.User;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;


@Service
@Slf4j
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    /** Stripe's accepted bounds for Checkout Session expires_at. */
    private static final Duration MIN_SESSION_LIFETIME = Duration.ofMinutes(30);
    private static final Duration MAX_SESSION_LIFETIME = Duration.ofHours(24);

    @Override
    public CheckoutSession getCheckoutSession(Booking booking, String successUrl, String failureUrl) {
        log.info("Creating session for booking with ID: {}", booking.getId());
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        try {
            CustomerCreateParams customerCreateParams = CustomerCreateParams.builder()
                    .setName(user.getName())
                    .setEmail(user.getEmail())
                    .build();

            Customer customer = Customer.create(
                    customerCreateParams
            );
            SessionCreateParams sessionParams = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
                    .setCustomer(customer.getId())
                    .setExpiresAt(sessionExpiresAt(booking))
                    // Lets the webhook resolve the booking even if storing the
                    // session id fails, so a captured payment is never orphaned.
                    .setClientReferenceId(String.valueOf(booking.getId()))
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(failureUrl)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("usd")
                                                    .setUnitAmount(booking.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
                                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                            .setName(booking.getHotel().getName() + " : " + booking.getRoom().getType())
                                                            .setDescription("Booking ID: " + booking.getId())
                                                            .build())
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            Session session = Session.create(sessionParams);

            log.info("Created checkout session {} for booking {}", session.getId(), booking.getId());

            return new CheckoutSession(session.getId(), session.getUrl());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Ties the Checkout Session's lifetime to the booking hold, so the session
     * cannot outlive the inventory it is paying for. Stripe only accepts an
     * expiry between 30 minutes and 24 hours from creation, so a hold outside
     * that window is clamped — meaning a payment hold shorter than 30 minutes
     * cannot be enforced on Stripe's side and the reconciliation path in
     * BookingService remains the backstop.
     */
    private long sessionExpiresAt(Booking booking) {
        long now = Instant.now().getEpochSecond();
        long earliest = now + MIN_SESSION_LIFETIME.toSeconds();
        long latest = now + MAX_SESSION_LIFETIME.toSeconds();

        LocalDateTime hold = booking.getHoldExpiresAt();
        long desired = hold != null
                ? hold.atZone(ZoneId.systemDefault()).toEpochSecond()
                : earliest;

        long clamped = Math.min(Math.max(desired, earliest), latest);
        if (clamped != desired) {
            log.warn("Booking {} hold expires at {}, outside Stripe's {}-{} session window; using {}",
                    booking.getId(), hold, MIN_SESSION_LIFETIME, MAX_SESSION_LIFETIME, Instant.ofEpochSecond(clamped));
        }
        return clamped;
    }
}
