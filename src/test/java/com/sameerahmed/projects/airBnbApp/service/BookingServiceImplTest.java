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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private NotificationService notificationService;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private User owner;
    private User otherUser;
    private Booking booking;
    private com.sameerahmed.projects.airBnbApp.entity.Room room;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bookingService, "frontendUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(bookingService, "reservationHoldMinutes", 10);
        ReflectionTestUtils.setField(bookingService, "paymentHoldMinutes", 30);
        ReflectionTestUtils.setField(bookingService, "freeCancelDays", 7);
        ReflectionTestUtils.setField(bookingService, "partialRefundPercent", 50);

        owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.com");
        owner.setRoles(java.util.Set.of(com.sameerahmed.projects.airBnbApp.entity.enums.Role.GUEST));

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setEmail("other@example.com");
        otherUser.setRoles(java.util.Set.of(com.sameerahmed.projects.airBnbApp.entity.enums.Role.GUEST));

        room = new com.sameerahmed.projects.airBnbApp.entity.Room();
        room.setId(5L);

        booking = Booking.builder()
                .id(10L)
                .user(owner)
                .room(room)
                .bookingStatus(BookingStatus.RESERVED)
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .roomsCount(1)
                .amount(BigDecimal.valueOf(200))
                .guests(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .holdExpiresAt(LocalDateTime.now().plusMinutes(10))
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
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(bookingRepository.findByBookingStatusInAndHoldExpiresAtBefore(any(), any()))
                .thenReturn(List.of(booking));

        bookingService.expireStaleBookings();

        verify(inventoryRepository).cancelReservation(5L, booking.getCheckInDate(), booking.getCheckOutDate(), 1);
        assertEquals(BookingStatus.EXPIRED, booking.getBookingStatus());
        verify(bookingRepository).save(booking);
    }

    @Test
    void cancelUnpaidBookingReleasesHoldWithoutRefund() {
        authenticate(owner);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bookingService.cancelPayment(10L);

        verify(inventoryRepository).cancelReservation(eq(5L), any(), any(), eq(1));
        verify(inventoryRepository, never()).cancelBooking(anyLong(), any(), any(), anyInt());
        assertEquals(BookingStatus.CANCELLED, booking.getBookingStatus());
        assertEquals(BigDecimal.ZERO, booking.getRefundAmount());
        verify(notificationService).sendBookingCancelled(booking, BigDecimal.ZERO);
    }

    @Test
    void initiatePaymentsExtendsHoldAndAllowsReservedWithoutGuests() {
        authenticate(owner);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(checkoutService.getCheckoutSession(any(), any(), any())).thenReturn("https://checkout.stripe.test");
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        String url = bookingService.initiatePayments(10L);

        assertEquals("https://checkout.stripe.test", url);
        assertEquals(BookingStatus.PAYMENT_PENDING, booking.getBookingStatus());
        assertTrue(booking.getHoldExpiresAt().isAfter(before.plusMinutes(25)));
        assertFalse(booking.getExpiryWarningSent());
    }

    @Test
    void hasBookingExpiredUsesHoldExpiresAt() {
        booking.setHoldExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertTrue(bookingService.hasBookingExpired(booking));

        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(5));
        assertFalse(bookingService.hasBookingExpired(booking));

        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setHoldExpiresAt(LocalDateTime.now().minusHours(1));
        assertFalse(bookingService.hasBookingExpired(booking));
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
    }
}
