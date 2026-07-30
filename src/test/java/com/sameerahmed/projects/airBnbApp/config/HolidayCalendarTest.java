package com.sameerahmed.projects.airBnbApp.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HolidayCalendarTest {

    @Test
    void matchesConfiguredMonthDay() {
        HolidayCalendar calendar = new HolidayCalendar();
        calendar.setDates(Set.of("01-01", "12-25"));

        assertTrue(calendar.isHoliday(LocalDate.of(2026, 1, 1)));
        assertTrue(calendar.isHoliday(LocalDate.of(2027, 12, 25)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 7, 4)));
    }

    @Test
    void emptyCalendarNeverHolidays() {
        HolidayCalendar calendar = new HolidayCalendar();
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 1, 1)));
    }
}
