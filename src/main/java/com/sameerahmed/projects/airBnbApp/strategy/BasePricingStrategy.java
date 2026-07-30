package com.sameerahmed.projects.airBnbApp.strategy;

import com.sameerahmed.projects.airBnbApp.entity.Inventory;

import java.math.BigDecimal;

public class BasePricingStrategy implements PricingStrategy{

    /**
     * Deliberately the room's base price and not {@code inventory.getPrice()}.
     *
     * <p>The chain above this multiplies by surge, occupancy, urgency and holiday
     * factors, and the result is written back to {@code inventory.price}. Starting
     * from that stored value would compound the same multipliers on every run of the
     * pricing job, so prices would climb without bound.
     */
    @Override
    public BigDecimal calculatePrice(Inventory inventory) {
        return inventory.getRoom().getBasePrice();
    }
}
