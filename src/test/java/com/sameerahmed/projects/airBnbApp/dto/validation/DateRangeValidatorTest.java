package com.sameerahmed.projects.airBnbApp.dto.validation;

import com.sameerahmed.projects.airBnbApp.dto.BookingRequest;
import com.sameerahmed.projects.airBnbApp.dto.HotelSearchRequest;
import com.sameerahmed.projects.airBnbApp.dto.UpdateInventoryRequestDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

    /**
     * Equal dates describe a stay of zero nights, which the inclusive convention
     * used to accept and then bill as one night.
     */
    @Test
    void bookingInvalidWhenCheckoutSameDayAsCheckin() {
        BookingRequest request = new BookingRequest();
        request.setHotelId(1L);
        request.setRoomId(1L);
        request.setCheckInDate(LocalDate.of(2026, 8, 10));
        request.setCheckOutDate(LocalDate.of(2026, 8, 10));
        request.setRoomsCount(1);

        Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    /** Administrative windows are inclusive, so closing a single date is valid. */
    @Test
    void inventoryUpdateValidForSingleDay() {
        UpdateInventoryRequestDto request = new UpdateInventoryRequestDto();
        request.setStartDate(LocalDate.of(2026, 8, 10));
        request.setEndDate(LocalDate.of(2026, 8, 10));
        request.setClosed(true);
        request.setSurgeFactor(BigDecimal.ONE);

        Set<ConstraintViolation<UpdateInventoryRequestDto>> violations = validator.validate(request);
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
