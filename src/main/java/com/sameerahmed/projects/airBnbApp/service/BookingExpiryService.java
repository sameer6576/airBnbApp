package com.sameerahmed.projects.airBnbApp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryService {

    private final BookingService bookingService;

    @Scheduled(cron = "0 * * * * *")
    public void expireStaleBookings() {
        log.debug("Running stale booking expiry job");
        bookingService.expireStaleBookings();
    }

    @Scheduled(cron = "30 * * * * *")
    public void sendExpiryWarnings() {
        log.debug("Running booking expiry warning job");
        bookingService.sendExpiryWarnings();
    }
}
