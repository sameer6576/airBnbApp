package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.*;
import com.stripe.model.Event;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BookingService {
    BookingDto initialiseBooking(BookingRequest bookingRequest);

    BookingDto addGuests(Long bookingId, List<GuestDto> guestDtos);

    String initiatePayments(Long bookingId);

    void capturePayment(Event event);

    void cancelPayment(Long bookingId);

    String getBookingStatus(Long bookingId);

    List<BookingDto> getAllBookingsByHotelId(Long hotelId);

    HotelReportDto getReportByHotelId(Long hotelId, LocalDate startDate, LocalDate endDate);

    List<BookingDto> getMyBookings();

    UserDto getMyProfile();
}
