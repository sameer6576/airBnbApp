package com.sameerahmed.projects.airBnbApp.dto.validation;

import com.sameerahmed.projects.airBnbApp.dto.BookingRequest;
import com.sameerahmed.projects.airBnbApp.dto.HotelSearchRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateRangeValidatorTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void hotelSearchValidWhenEndOnOrAfterStart() {
        HotelSearchRequest request = new HotelSearchRequest();
        request.setCity("Paris");
        request.setStartDate(LocalDate.of(2026, 8, 10));
        request.setEndDate(LocalDate.of(2026, 8, 12));
        request.setRoomsCount(1);

        Set<ConstraintViolation<HotelSearchRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void bookingInvalidWhenCheckoutBeforeCheckin() {
        BookingRequest request = new BookingRequest();
        request.setHotelId(1L);
        request.setRoomId(1L);
        request.setCheckInDate(LocalDate.of(2026, 8, 12));
        request.setCheckOutDate(LocalDate.of(2026, 8, 10));
        request.setRoomsCount(1);

        Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }
}
