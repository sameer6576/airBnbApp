package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.repository.HotelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sweeps every hotel and recomputes its dynamic inventory prices.
 *
 * <p>Deliberately not transactional. Wrapping the whole sweep in one transaction
 * held a single connection and every lock it took for the duration, bloated the
 * write-ahead log, and meant one bad hotel at the end rolled back the work done
 * for all the others. Each hotel now commits on its own via {@link
 * HotelPricingRefresher}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PricingUpdateService {

    private static final int BATCH_SIZE = 100;

    private final HotelRepository hotelRepository;
    private final HotelPricingRefresher hotelPricingRefresher;

    @Scheduled(cron = "${app.scheduling.pricing-update.cron:0 0 * * * *}")
    public void updatePrices() {
        int page = 0;
        int refreshed = 0;
        int failed = 0;

        while (true) {
            // Sorting by id is not cosmetic: findAll without an explicit sort has
            // no ordering guarantee between queries, so paging over rows that are
            // being written can silently skip or repeat hotels.
            Page<Hotel> hotelPage = hotelRepository.findAll(
                    PageRequest.of(page, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id")));

            if (hotelPage.isEmpty()) {
                break;
            }

            for (Hotel hotel : hotelPage.getContent()) {
                try {
                    hotelPricingRefresher.refresh(hotel);
                    refreshed++;
                } catch (RuntimeException e) {
                    // One unpriceable hotel must not abort the sweep. Previously a
                    // single failure rolled back every hotel processed before it.
                    failed++;
                    log.error("Failed to refresh prices for hotel {}", hotel.getId(), e);
                }
            }

            if (!hotelPage.hasNext()) {
                break;
            }
            page++;
        }

        if (failed > 0) {
            log.warn("Pricing sweep finished: {} hotels refreshed, {} failed", refreshed, failed);
        } else {
            log.info("Pricing sweep finished: {} hotels refreshed", refreshed);
        }
    }
}
