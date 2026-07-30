package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.Inventory;
import com.sameerahmed.projects.airBnbApp.repository.InventoryRepository;
import com.sameerahmed.projects.airBnbApp.strategy.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Repricing for a single hotel, in its own transaction.
 *
 * <p>This lives in its own bean rather than as a method on {@link
 * PricingUpdateService} because Spring's transaction support works through a
 * proxy: a call from one method of a bean to another on the same instance does
 * not pass through that proxy, so {@code @Transactional} on a self-invoked method
 * has no effect. Splitting the class is what actually gives one transaction per
 * hotel instead of one transaction for the whole sweep.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HotelPricingRefresher {

    private final InventoryRepository inventoryRepository;
    private final PricingService pricingService;
    private final HotelMinPriceService hotelMinPriceService;

    @Transactional
    public void refresh(Hotel hotel) {
        log.debug("Refreshing prices for hotel {}", hotel.getId());

        // Inclusive administrative window: the whole bookable horizon, not a stay.
        List<Inventory> inventoryList = inventoryRepository.findByHotelAndDateBetween(
                hotel,
                LocalDate.now(),
                LocalDate.now().plusYears(1));

        inventoryList.forEach(inventory -> {
            BigDecimal dynamicPrice = pricingService.calculateDynamicPricing(inventory);
            inventory.setPrice(dynamicPrice);
        });
        inventoryRepository.saveAll(inventoryList);

        hotelMinPriceService.updateHotelMinPrice(hotel.getId());
    }
}
