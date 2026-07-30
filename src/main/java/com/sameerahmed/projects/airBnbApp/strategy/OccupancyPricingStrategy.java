package com.sameerahmed.projects.airBnbApp.strategy;

import com.sameerahmed.projects.airBnbApp.entity.Inventory;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@RequiredArgsConstructor
public class OccupancyPricingStrategy implements PricingStrategy{
    private final PricingStrategy wrapped;

    @Override
    public BigDecimal calculatePrice(Inventory inventory) {
        BigDecimal price = wrapped.calculatePrice(inventory);

        // A room with no units has no occupancy. Dividing by zero yielded Infinity
        // when anything was booked, which then applied the surcharge.
        Integer totalCount = inventory.getTotalCount();
        if (totalCount == null || totalCount == 0) {
            return price;
        }

        double occupancyRate = (double) inventory.getBookedCount() / totalCount;
        if(occupancyRate > 0.8){
            price = price.multiply(BigDecimal.valueOf(1.2));
        }
        return price;
    }
}
