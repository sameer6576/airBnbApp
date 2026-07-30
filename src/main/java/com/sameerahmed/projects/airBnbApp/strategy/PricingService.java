package com.sameerahmed.projects.airBnbApp.strategy;

import com.sameerahmed.projects.airBnbApp.config.HolidayCalendar;
import com.sameerahmed.projects.airBnbApp.entity.Inventory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final HolidayCalendar holidayCalendar;

    /** Money scale. Matches the numeric(10,2) columns these values are stored in. */
    private static final int MONEY_SCALE = 2;

    /**
     * Computes the nightly price the pricing job writes into {@code inventory.price}.
     *
     * <p>Rounded once, at the end. Each multiplier adds decimal places —
     * {@code BigDecimal.multiply} sums the operands' scales — so an unrounded chain
     * produced a scale-9 value that the numeric(10,2) column then rounded silently
     * on the way in. That is how the amount in a response could differ from the
     * amount actually stored and charged.
     */
    public BigDecimal calculateDynamicPricing(Inventory inventory) {
        PricingStrategy pricingStrategy = new BasePricingStrategy();
        pricingStrategy = new SurgePricingStrategy(pricingStrategy);
        pricingStrategy = new OccupancyPricingStrategy(pricingStrategy);
        pricingStrategy = new UrgencyPricingStrategy(pricingStrategy);
        pricingStrategy = new HolidayPricingStrategy(pricingStrategy, holidayCalendar);
        return pricingStrategy.calculatePrice(inventory).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Sums the stored nightly prices for a stay.
     *
     * <p>Reads {@code inventory.price} rather than recomputing, because that column
     * is the single source of truth: search aggregates the same values into
     * HotelMinPrice. Recomputing here meant a guest could be shown one price in
     * search and quoted a different one at booking, whenever the pricing job had
     * not yet caught up with a surge or occupancy change.
     */
    public BigDecimal calculateTotalPrice(List<Inventory> inventoryList) {
        return inventoryList.stream()
                .map(Inventory::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
