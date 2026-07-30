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
 * Rolls the bookable window forward.
 *
 * <p>Inventory used to be generated exactly once, when a hotel was activated, and
 * never extended. Because generation always started from "today", the horizon shrank
 * by a day every day: a hotel activated with a year of inventory had six months of
 * it a year later and none at all after that, with searches simply returning nothing
 * and no error to explain why.
 *
 * <p>This job re-asserts the horizon daily. It is safe to run at any frequency, and
 * safe to miss a run: {@code ensureInventoryHorizon} generates only the dates that
 * are absent, so a gap left by downtime is filled on the next pass.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryHorizonService {

    private static final int BATCH_SIZE = 50;

    private final HotelRepository hotelRepository;
    private final InventoryService inventoryService;

    @Scheduled(cron = "${app.scheduling.inventory-horizon.cron:0 30 3 * * *}")
    public void extendInventoryHorizon() {
        int page = 0;
        int hotelsProcessed = 0;
        int failed = 0;

        while (true) {
            // Only active hotels have inventory to extend. Sorted by id because
            // unsorted pagination has no ordering guarantee between queries.
            Page<Hotel> hotelPage = hotelRepository.findByActiveTrue(
                    PageRequest.of(page, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id")));

            if (hotelPage.isEmpty()) {
                break;
            }

            for (Hotel hotel : hotelPage.getContent()) {
                try {
                    inventoryService.refreshHotelInventory(hotel);
                    hotelsProcessed++;
                } catch (RuntimeException e) {
                    failed++;
                    log.error("Failed to extend inventory horizon for hotel {}", hotel.getId(), e);
                }
            }

            if (!hotelPage.hasNext()) {
                break;
            }
            page++;
        }

        if (failed > 0) {
            log.warn("Inventory horizon job finished: {} hotels processed, {} failed", hotelsProcessed, failed);
        } else {
            log.debug("Inventory horizon job finished: {} hotels processed", hotelsProcessed);
        }
    }
}
