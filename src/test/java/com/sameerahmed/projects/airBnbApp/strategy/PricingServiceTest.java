package com.sameerahmed.projects.airBnbApp.strategy;

import com.sameerahmed.projects.airBnbApp.config.HolidayCalendar;
import com.sameerahmed.projects.airBnbApp.entity.Inventory;
import com.sameerahmed.projects.airBnbApp.entity.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingServiceTest {

    private PricingService pricingService;
    private HolidayCalendar holidayCalendar;

    @BeforeEach
    void setUp() {
        holidayCalendar = new HolidayCalendar();
        holidayCalendar.setDates(Set.of("01-01", "12-25"));
        pricingService = new PricingService(holidayCalendar);
    }

    @Test
    void basePriceUsedWhenNoModifiersApply() {
        Inventory inventory = inventoryOn(LocalDate.now().plusDays(30), BigDecimal.valueOf(100), 0, 10);
        BigDecimal price = pricingService.calculateDynamicPricing(inventory);
        assertEquals(0, price.compareTo(BigDecimal.valueOf(100)));
    }

    @Test
    void holidayMultiplierAppliedOnConfiguredDate() {
        Inventory inventory = inventoryOn(LocalDate.of(2026, 12, 25), BigDecimal.valueOf(100), 0, 10);
        BigDecimal price = pricingService.calculateDynamicPricing(inventory);
        assertEquals(0, price.compareTo(BigDecimal.valueOf(125)));
    }

    @Test
    void occupancyMultiplierAppliedAboveEightyPercent() {
        Inventory inventory = inventoryOn(LocalDate.now().plusDays(30), BigDecimal.valueOf(100), 9, 10);
        BigDecimal price = pricingService.calculateDynamicPricing(inventory);
        assertEquals(0, price.compareTo(BigDecimal.valueOf(120)));
    }

    @Test
    void calculateTotalPriceSumsDays() {
        Inventory day1 = inventoryOn(LocalDate.now().plusDays(30), BigDecimal.valueOf(100), 0, 10);
        Inventory day2 = inventoryOn(LocalDate.now().plusDays(31), BigDecimal.valueOf(100), 0, 10);
        BigDecimal total = pricingService.calculateTotalPrice(List.of(day1, day2));
        assertEquals(0, total.compareTo(BigDecimal.valueOf(200)));
    }

    @Test
    void urgencyAppliesWithinSevenDays() {
        Inventory inventory = inventoryOn(LocalDate.now().plusDays(3), BigDecimal.valueOf(100), 0, 10);
        BigDecimal price = pricingService.calculateDynamicPricing(inventory);
        assertTrue(price.compareTo(BigDecimal.valueOf(100)) > 0);
    }

    private Inventory inventoryOn(LocalDate date, BigDecimal basePrice, int booked, int total) {
        Room room = new Room();
        room.setBasePrice(basePrice);
        room.setTotalCount(total);

        return Inventory.builder()
                .date(date)
                .room(room)
                .bookedCount(booked)
                .reservedCount(0)
                .totalCount(total)
                .surgeFactor(BigDecimal.ONE)
                .price(basePrice)
                .city("Test City")
                .closed(false)
                .build();
    }
}
