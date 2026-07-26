package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.GuestDto;
import com.sameerahmed.projects.airBnbApp.entity.Booking;
import com.sameerahmed.projects.airBnbApp.entity.User;
import com.sameerahmed.projects.airBnbApp.entity.enums.BookingStatus;
import com.sameerahmed.projects.airBnbApp.repository.*;
import com.sameerahmed.projects.airBnbApp.strategy.PricingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryService inventoryService;
    @Mock private ModelMapper modelMapper;
    @Mock private GuestRepository guestRepository;
    @Mock private CheckoutService checkoutService;
    @Mock private PricingService pricingService;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private User owner;
    private User otherUser;
    private Booking booking;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bookingService, "frontendUrl", "http://localhost:8080");

        owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");
        owner.setRoles(java.util.Set.of(com.sameerahmed.projects.airBnbApp.entity.enums.Role.GUEST));

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setEmail("other@example.com");
        otherUser.setRoles(java.util.Set.of(com.sameerahmed.projects.airBnbApp.entity.enums.Role.GUEST));

        booking = Booking.builder()
                .id(10L)
                .user(owner)
                .bookingStatus(BookingStatus.RESERVED)
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .roomsCount(1)
                .amount(BigDecimal.valueOf(200))
                .guests(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getBookingStatusAllowsOwner() {
        authenticate(owner);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        String status = bookingService.getBookingStatus(10L);
        assertEquals(BookingStatus.RESERVED.name(), status);
    }

    @Test
    void getBookingStatusDeniesNonOwner() {
        authenticate(otherUser);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThrows(AccessDeniedException.class, () -> bookingService.getBookingStatus(10L));
    }

    @Test
    void addGuestsDeniesNonOwner() {
        authenticate(otherUser);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        GuestDto guestDto = new GuestDto();
        guestDto.setName("Alice");

        assertThrows(AccessDeniedException.class, () -> bookingService.addGuests(10L, List.of(guestDto)));
    }

    @Test
    void capturePaymentIsIdempotentWhenAlreadyConfirmed() {
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentSessionId("cs_test_123");
        when(bookingRepository.findByPaymentSessionId("cs_test_123")).thenReturn(Optional.of(booking));

        com.stripe.model.Event event = org.mockito.Mockito.mock(com.stripe.model.Event.class);
        com.stripe.model.EventDataObjectDeserializer deserializer =
                org.mockito.Mockito.mock(com.stripe.model.EventDataObjectDeserializer.class);
        com.stripe.model.checkout.Session session =
                org.mockito.Mockito.mock(com.stripe.model.checkout.Session.class);

        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(session));
        when(session.getId()).thenReturn("cs_test_123");

        bookingService.capturePayment(event);

        verify(inventoryRepository, never()).confirmBooking(anyLong(), any(), any(), anyInt());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void expireStaleBookingsReleasesReservation() {
        com.sameerahmed.projects.airBnbApp.entity.Room room =
                new com.sameerahmed.projects.airBnbApp.entity.Room();
        room.setId(5L);
        booking.setRoom(room);
        booking.setCreatedAt(LocalDateTime.now().minusMinutes(15));

        when(bookingRepository.findByBookingStatusInAndCreatedAtBefore(any(), any()))
                .thenReturn(List.of(booking));

        bookingService.expireStaleBookings();

        verify(inventoryRepository).cancelReservation(5L, booking.getCheckInDate(), booking.getCheckOutDate(), 1);
        assertEquals(BookingStatus.EXPIRED, booking.getBookingStatus());
        verify(bookingRepository).save(booking);
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
    }
}
