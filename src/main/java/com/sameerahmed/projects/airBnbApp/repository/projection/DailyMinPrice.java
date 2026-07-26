package com.sameerahmed.projects.airBnbApp.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyMinPrice {

    LocalDate getDate();

    BigDecimal getPrice();

}