package com.sameerahmed.projects.airBnbApp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "app.holidays")
@Getter
@Setter
public class HolidayCalendar {

    // MM-dd e.g. 01-01, 12-25
    private Set<String> dates = new HashSet<>();

    public boolean isHoliday(LocalDate date) {
        String mmDd = String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth());
        return dates.contains(mmDd);
    }
}
