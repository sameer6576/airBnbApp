package com.sameerahmed.projects.airBnbApp.strategy;

import com.sameerahmed.projects.airBnbApp.config.HolidayCalendar;
import com.sameerahmed.projects.airBnbApp.entity.Inventory;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@RequiredArgsConstructor
public class HolidayPricingStrategy implements PricingStrategy {

    private final PricingStrategy wrapped;
    private final HolidayCalendar holidayCalendar;

    @Override
    public BigDecimal calculatePrice(Inventory inventory) {
        BigDecimal price = wrapped.calculatePrice(inventory);
        if (holidayCalendar.isHoliday(inventory.getDate())) {
            price = price.multiply(BigDecimal.valueOf(1.25));
        }
        return price;
    }
}
