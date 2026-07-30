package com.sameerahmed.projects.airBnbApp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Both schedules are properties rather than literals, so quieting a noisy local
 * log cannot silently change production timing — which is exactly what happened
 * when these were hardcoded and got edited to run hourly against a ten-minute
 * hold. Setting a cron to "-" disables that job.
 *
 * <p>The expiry job must run at least as often as the shortest hold, or inventory
 * stays unsellable well past its deadline and dates look falsely sold out.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryService {

    private final BookingService bookingService;

    @Scheduled(cron = "${app.scheduling.booking-expiry.cron:0 * * * * *}")
    public void expireStaleBookings() {
        log.debug("Running stale booking expiry job");
        try {
            bookingService.expireStaleBookings();
        } catch (RuntimeException e) {
            // Spring's default handler logs and drops the run. Logging here as an
            // error makes a silently failing revenue-critical job visible.
            log.error("Stale booking expiry job failed", e);
        }
    }

    /**
     * Offset from the expiry job so the two do not contend on the same booking and
     * inventory rows. The warning window is only a couple of minutes wide, so this
     * job is pointless unless it runs at least that often.
     */
    @Scheduled(cron = "${app.scheduling.expiry-warning.cron:30 * * * * *}")
    public void sendExpiryWarnings() {
        log.debug("Running booking expiry warning job");
        try {
            bookingService.sendExpiryWarnings();
        } catch (RuntimeException e) {
            log.error("Booking expiry warning job failed", e);
        }
    }
}
