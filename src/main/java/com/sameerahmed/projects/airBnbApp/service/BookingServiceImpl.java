package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.*;
import com.sameerahmed.projects.airBnbApp.entity.*;
import com.sameerahmed.projects.airBnbApp.entity.enums.BookingStatus;
import com.sameerahmed.projects.airBnbApp.exception.ResourceNotFoundException;
import com.sameerahmed.projects.airBnbApp.repository.*;
import com.sameerahmed.projects.airBnbApp.strategy.PricingService;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sameerahmed.projects.airBnbApp.util.AppUtils.getCurrentUser;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final ModelMapper modelMapper;
    private final GuestRepository guestRepository;
    private final CheckoutService checkoutService;
    private final PricingService pricingService;
    private final NotificationService notificationService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${app.cancellation.free-cancel-days:7}")
    private int freeCancelDays;

    @Value("${app.cancellation.partial-refund-percent:50}")
    private int partialRefundPercent;

    @Override
    @Transactional
    public BookingDto initialiseBooking(BookingRequest bookingRequest) {
        return initialiseBooking(bookingRequest, null);
    }

    @Override
    @Transactional
    public BookingDto initialiseBooking(BookingRequest bookingRequest, String idempotencyKey) {
        User currentUser = getCurrentUser();
        String fingerprint = buildIdempotencyFingerprint(bookingRequest);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String scopedKey = currentUser.getId() + ":" + idempotencyKey.trim();
            Optional<Booking> existing = bookingRepository.findByIdempotencyKey(scopedKey);
            if (existing.isPresent()) {
                Booking previous = existing.get();
                if (!Objects.equals(previous.getIdempotencyFingerprint(), fingerprint)) {
                    throw new IllegalArgumentException(
                            "Idempotency-Key was already used with a different booking request");
                }
                log.info("Returning existing booking {} for idempotency key", previous.getId());
                return modelMapper.map(previous, BookingDto.class);
            }
            return createNewBooking(bookingRequest, currentUser, scopedKey, fingerprint);
        }

        return createNewBooking(bookingRequest, currentUser, null, null);
    }

    private BookingDto createNewBooking(BookingRequest bookingRequest,
                                        User currentUser,
                                        String scopedIdempotencyKey,
                                        String fingerprint) {
        log.info("Initialising booking for hotel: {}, room: {}, date {} - {}",
                bookingRequest.getHotelId(), bookingRequest.getRoomId(),
                bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate());

        Hotel hotel = hotelRepository.findById(bookingRequest.getHotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with id: " + bookingRequest.getHotelId()));

        Room room = roomRepository.findById(bookingRequest.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + bookingRequest.getRoomId()));

        List<Inventory> inventoryList = inventoryRepository
                .findAndLockAvailableInventory(bookingRequest.getRoomId(), bookingRequest.getCheckInDate(),
                        bookingRequest.getCheckOutDate(), bookingRequest.getRoomsCount());
        long daysCount = ChronoUnit.DAYS.between(bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate()) + 1;
        if (inventoryList.size() != daysCount) {
            throw new IllegalStateException("Room is not available anymore");
        }

        inventoryRepository.initBooking(bookingRequest.getRoomId(), bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate(), bookingRequest.getRoomsCount());

        BigDecimal priceForOneRoom = pricingService.calculateTotalPrice(inventoryList);
        BigDecimal totalPrice = priceForOneRoom.multiply(BigDecimal.valueOf(bookingRequest.getRoomsCount()));

        Booking booking = Booking.builder()
                .bookingStatus(BookingStatus.RESERVED)
                .hotel(hotel)
                .room(room)
                .checkInDate(bookingRequest.getCheckInDate())
                .checkOutDate(bookingRequest.getCheckOutDate())
                .user(currentUser)
                .roomsCount(bookingRequest.getRoomsCount())
                .amount(totalPrice)
                .guests(new HashSet<>())
                .expiryWarningSent(false)
                .idempotencyKey(scopedIdempotencyKey)
                .idempotencyFingerprint(fingerprint)
                .build();

        booking = bookingRepository.save(booking);
        return modelMapper.map(booking, BookingDto.class);
    }

    private String buildIdempotencyFingerprint(BookingRequest request) {
        return request.getHotelId() + "|"
                + request.getRoomId() + "|"
                + request.getCheckInDate() + "|"
                + request.getCheckOutDate() + "|"
                + request.getRoomsCount();
    }

    @Override
    @Transactional
    public BookingDto addGuests(Long bookingId, List<GuestDto> guestDtoList) {
        log.info("Adding guests with booking id: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        User user = getCurrentUser();
        assertBookingOwner(booking, user);

        if (hasBookingExpired(booking)) {
            throw new IllegalStateException("Booking has already expired");
        }

        if (booking.getBookingStatus() != BookingStatus.RESERVED) {
            throw new IllegalStateException("Booking is not under reserved state, cannot add guests");
        }

        for (GuestDto guestDto : guestDtoList) {
            Guest guest;
            if (guestDto.getId() != null) {
                guest = guestRepository.findById(guestDto.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Guest not found with id: " + guestDto.getId()));
                if (!Objects.equals(user.getId(), guest.getUser().getId())) {
                    throw new AccessDeniedException("Guest does not belong to this user with id: " + user.getId());
                }
            } else {
                guest = modelMapper.map(guestDto, Guest.class);
                guest.setUser(user);
                guest = guestRepository.save(guest);
            }
            booking.getGuests().add(guest);
        }
        booking.setBookingStatus(BookingStatus.GUEST_ADDED);
        bookingRepository.save(booking);

        return modelMapper.map(booking, BookingDto.class);

    }

    @Override
    @Transactional
    public String initiatePayments(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        User user = getCurrentUser();
        assertBookingOwner(booking, user);

        if (hasBookingExpired(booking)) {
            throw new IllegalStateException("Booking has already expired");
        }

        String sessionUrl = checkoutService.getCheckoutSession(booking, frontendUrl + "/payments/success", frontendUrl + "/payments/failure");

        booking.setBookingStatus(BookingStatus.PAYMENT_PENDING);
        bookingRepository.save(booking);
        return sessionUrl;
    }

    @Override
    @Transactional
    public void capturePayment(Event event) {
        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
            if (session == null) return;

            String sessionId = session.getId();

            Booking booking = bookingRepository.findByPaymentSessionId(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found for session id: " + sessionId));

            if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
                log.info("Booking already confirmed for session id: {}", sessionId);
                return;
            }

            booking.setBookingStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);

            inventoryRepository.findAndLockReservedInventory(booking.getRoom().getId(), booking.getCheckInDate(),
                    booking.getCheckOutDate(), booking.getRoomsCount());

            inventoryRepository.confirmBooking(booking.getRoom().getId(), booking.getCheckInDate(),
                    booking.getCheckOutDate(), booking.getRoomsCount());

            notificationService.sendBookingConfirmed(booking);
            log.info("Successfully confirmed the booking for Booking ID: {}", booking.getId());
        } else {
            log.warn("Unhandled event type: {}", event.getType());
        }
    }

    @Override
    @Transactional
    public void cancelPayment(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        User user = getCurrentUser();
        assertBookingOwner(booking, user);

        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be cancelled");
        }

        CancellationQuoteDto quote = buildCancellationQuote(booking);
        BigDecimal refundAmount = quote.getEstimatedRefund();

        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setRefundAmount(refundAmount);
        bookingRepository.save(booking);

        inventoryRepository.findAndLockReservedInventory(booking.getRoom().getId(), booking.getCheckInDate(),
                booking.getCheckOutDate(), booking.getRoomsCount());

        inventoryRepository.cancelBooking(booking.getRoom().getId(), booking.getCheckInDate(),
                booking.getCheckOutDate(), booking.getRoomsCount());

        if (refundAmount.compareTo(BigDecimal.ZERO) > 0 && booking.getPaymentSessionId() != null) {
            try {
                Session session = Session.retrieve(booking.getPaymentSessionId());
                RefundCreateParams.Builder refundBuilder = RefundCreateParams.builder()
                        .setPaymentIntent(session.getPaymentIntent());
                if (refundAmount.compareTo(booking.getAmount()) < 0) {
                    long cents = refundAmount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
                    refundBuilder.setAmount(cents);
                }
                Refund.create(refundBuilder.build());
            } catch (StripeException e) {
                throw new IllegalStateException("Refund failed: " + e.getMessage());
            }
        }

        notificationService.sendBookingCancelled(booking, refundAmount);
        log.info("Successfully cancelled booking {} with refund {}", booking.getId(), refundAmount);
    }

    @Override
    public CancellationQuoteDto getCancellationQuote(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        assertBookingOwner(booking, getCurrentUser());
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Cancellation quote is only available for confirmed bookings");
        }
        return buildCancellationQuote(booking);
    }

    @Override
    @Transactional
    public BookingDto modifyBookingDates(Long bookingId, ModifyBookingRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        User user = getCurrentUser();
        assertBookingOwner(booking, user);

        if (booking.getBookingStatus() != BookingStatus.RESERVED
                && booking.getBookingStatus() != BookingStatus.GUEST_ADDED
                && booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Booking cannot be modified in status " + booking.getBookingStatus());
        }
        if (hasBookingExpired(booking) && booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Booking has already expired");
        }

        Long roomId = booking.getRoom().getId();
        LocalDate oldIn = booking.getCheckInDate();
        LocalDate oldOut = booking.getCheckOutDate();
        int oldCount = booking.getRoomsCount();

        List<Inventory> inventoryList = inventoryRepository.findAndLockAvailableInventory(
                roomId, request.getCheckInDate(), request.getCheckOutDate(), request.getRoomsCount());
        long daysCount = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate()) + 1;
        if (inventoryList.size() != daysCount) {
            throw new IllegalStateException("Room is not available for the new dates");
        }

        if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            inventoryRepository.findAndLockReservedInventory(roomId, oldIn, oldOut, oldCount);
            inventoryRepository.cancelBooking(roomId, oldIn, oldOut, oldCount);
            inventoryRepository.initBooking(roomId, request.getCheckInDate(), request.getCheckOutDate(), request.getRoomsCount());
            inventoryRepository.findAndLockReservedInventory(roomId, request.getCheckInDate(), request.getCheckOutDate(), request.getRoomsCount());
            inventoryRepository.confirmBooking(roomId, request.getCheckInDate(), request.getCheckOutDate(), request.getRoomsCount());
        } else {
            inventoryRepository.findAndLockReservedInventory(roomId, oldIn, oldOut, oldCount);
            inventoryRepository.cancelReservation(roomId, oldIn, oldOut, oldCount);
            inventoryRepository.initBooking(roomId, request.getCheckInDate(), request.getCheckOutDate(), request.getRoomsCount());
        }

        BigDecimal priceForOneRoom = pricingService.calculateTotalPrice(inventoryList);
        booking.setCheckInDate(request.getCheckInDate());
        booking.setCheckOutDate(request.getCheckOutDate());
        booking.setRoomsCount(request.getRoomsCount());
        booking.setAmount(priceForOneRoom.multiply(BigDecimal.valueOf(request.getRoomsCount())));
        return modelMapper.map(bookingRepository.save(booking), BookingDto.class);
    }

    @Override
    @Transactional
    public BookingDto replaceGuests(Long bookingId, List<GuestDto> guestDtoList) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        User user = getCurrentUser();
        assertBookingOwner(booking, user);

        if (booking.getBookingStatus() != BookingStatus.RESERVED
                && booking.getBookingStatus() != BookingStatus.GUEST_ADDED
                && booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Guests cannot be modified in status " + booking.getBookingStatus());
        }

        booking.getGuests().clear();
        for (GuestDto guestDto : guestDtoList) {
            Guest guest;
            if (guestDto.getId() != null) {
                guest = guestRepository.findById(guestDto.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Guest not found with id: " + guestDto.getId()));
                if (!Objects.equals(user.getId(), guest.getUser().getId())) {
                    throw new AccessDeniedException("Guest does not belong to this user with id: " + user.getId());
                }
            } else {
                guest = modelMapper.map(guestDto, Guest.class);
                guest.setUser(user);
                guest = guestRepository.save(guest);
            }
            booking.getGuests().add(guest);
        }
        if (!guestDtoList.isEmpty() && booking.getBookingStatus() == BookingStatus.RESERVED) {
            booking.setBookingStatus(BookingStatus.GUEST_ADDED);
        }
        return modelMapper.map(bookingRepository.save(booking), BookingDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public HotelAnalyticsDto getHotelAnalytics(Long hotelId, LocalDate startDate, LocalDate endDate) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with id: " + hotelId));
        User user = getCurrentUser();
        if (!Objects.equals(user.getId(), hotel.getOwner().getId())) {
            throw new AccessDeniedException("This user does not own this hotel with id: " + hotelId);
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        List<Booking> bookings = bookingRepository.findByHotelAndCreatedAtBetween(hotel, startDateTime, endDateTime);

        long total = bookings.size();
        long confirmed = bookings.stream().filter(b -> b.getBookingStatus() == BookingStatus.CONFIRMED).count();
        long cancelled = bookings.stream().filter(b -> b.getBookingStatus() == BookingStatus.CANCELLED).count();
        BigDecimal revenue = bookings.stream()
                .filter(b -> b.getBookingStatus() == BookingStatus.CONFIRMED)
                .map(Booking::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long inventoryDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        int totalRoomUnits = hotel.getRooms() == null ? 0 :
                hotel.getRooms().stream().mapToInt(Room::getTotalCount).sum();
        long capacityNights = Math.max(inventoryDays, 1) * Math.max(totalRoomUnits, 1);
        long bookedNights = bookings.stream()
                .filter(b -> b.getBookingStatus() == BookingStatus.CONFIRMED)
                .mapToLong(b -> (ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate()) + 1L) * b.getRoomsCount())
                .sum();
        double occupancy = Math.min(100.0, (bookedNights * 100.0) / capacityNights);
        double cancelRate = total == 0 ? 0.0 : (cancelled * 100.0) / total;

        List<HotelAnalyticsDto.RoomPerformanceDto> topRooms = bookings.stream()
                .filter(b -> b.getBookingStatus() == BookingStatus.CONFIRMED)
                .collect(Collectors.groupingBy(b -> b.getRoom().getId()))
                .entrySet().stream()
                .map(entry -> {
                    List<Booking> roomBookings = entry.getValue();
                    Room room = roomBookings.getFirst().getRoom();
                    BigDecimal roomRevenue = roomBookings.stream()
                            .map(Booking::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new HotelAnalyticsDto.RoomPerformanceDto(
                            room.getId(), room.getType(), (long) roomBookings.size(), roomRevenue);
                })
                .sorted((a, b) -> b.getRevenue().compareTo(a.getRevenue()))
                .limit(5)
                .collect(Collectors.toList());

        return new HotelAnalyticsDto(
                hotel.getId(),
                hotel.getName(),
                Math.round(occupancy * 10.0) / 10.0,
                Math.round(cancelRate * 10.0) / 10.0,
                total,
                confirmed,
                cancelled,
                revenue,
                topRooms
        );
    }

    @Override
    public String getBookingStatus(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        User user = getCurrentUser();
        assertBookingOwner(booking, user);
        return booking.getBookingStatus().name();
    }

    @Override
    public List<BookingDto> getAllBookingsByHotelId(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(
                () -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId)
        );

        User user = getCurrentUser();

        if (!Objects.equals(user.getId(), hotel.getOwner().getId())) {
            throw new AccessDeniedException("You are not the owner of hotel with ID: " + hotelId);
        }

        List<Booking> bookings = bookingRepository.findByHotel(hotel);

        return bookings.stream().map(element -> modelMapper.map(element, BookingDto.class))
                .collect(Collectors.toList());
    }

    @Override
    public HotelReportDto getReportByHotelId(Long hotelId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting the hotel with ID: {}", hotelId);
        Hotel hotel = hotelRepository
                .findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with id: " + hotelId));
        User user = getCurrentUser();

        if (!Objects.equals(user.getId(), hotel.getOwner().getId())) {
            throw new AccessDeniedException("This user does not own this hotel with id: " + hotelId);
        }
        log.info("Generating report for hotel with ID: {}", hotelId);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Booking> bookings = bookingRepository.findByHotelAndCreatedAtBetween(hotel, startDateTime, endDateTime);

        Long totalConfirmedBookings = bookings.stream()
                .filter(booking -> booking.getBookingStatus() == BookingStatus.CONFIRMED)
                .count();

        BigDecimal totalRevenueOfConfirmedBookings = bookings.stream()
                .filter(booking -> booking.getBookingStatus() == BookingStatus.CONFIRMED)
                .map(Booking::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgRevenue = totalConfirmedBookings == 0 ? BigDecimal.ZERO :
                totalRevenueOfConfirmedBookings.divide(BigDecimal.valueOf(totalConfirmedBookings), RoundingMode.HALF_UP);
        return new HotelReportDto(totalConfirmedBookings, totalRevenueOfConfirmedBookings, avgRevenue);
    }

    @Override
    public List<BookingDto> getMyBookings() {
        User user = getCurrentUser();

        List<Booking> bookings = bookingRepository.findByUser(user);
        return bookings
                .stream()
                .map((element) -> modelMapper.map(element, BookingDto.class))
                .collect(Collectors.toList());
    }

    @Override
    public UserDto getMyProfile() {
        User user = getCurrentUser();
        return modelMapper.map(user, UserDto.class);
    }

    public boolean hasBookingExpired(Booking booking) {
        return booking.getCreatedAt().plusMinutes(10).isBefore(LocalDateTime.now());
    }

    private void assertBookingOwner(Booking booking, User user) {
        if (!Objects.equals(user.getId(), booking.getUser().getId())) {
            throw new AccessDeniedException("Booking does not belong to this user with id: " + user.getId());
        }
    }

    private CancellationQuoteDto buildCancellationQuote(Booking booking) {
        long daysUntilCheckIn = ChronoUnit.DAYS.between(LocalDate.now(), booking.getCheckInDate());
        boolean free = daysUntilCheckIn >= freeCancelDays;
        BigDecimal refundPercent = free
                ? BigDecimal.valueOf(100)
                : BigDecimal.valueOf(partialRefundPercent);
        BigDecimal estimatedRefund = booking.getAmount()
                .multiply(refundPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (daysUntilCheckIn < 0) {
            estimatedRefund = BigDecimal.ZERO;
            refundPercent = BigDecimal.ZERO;
            free = false;
        }
        return new CancellationQuoteDto(
                free,
                (int) daysUntilCheckIn,
                freeCancelDays,
                refundPercent,
                estimatedRefund
        );
    }

    @Override
    @Transactional
    public void sendExpiryWarnings() {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(10);
        LocalDateTime windowEnd = LocalDateTime.now().minusMinutes(8);
        List<BookingStatus> statuses = List.of(
                BookingStatus.RESERVED,
                BookingStatus.GUEST_ADDED,
                BookingStatus.PAYMENT_PENDING
        );
        List<Booking> soonToExpire = bookingRepository
                .findByBookingStatusInAndExpiryWarningSentFalseAndCreatedAtBetween(statuses, windowStart, windowEnd);
        for (Booking booking : soonToExpire) {
            notificationService.sendBookingExpiryWarning(booking);
            booking.setExpiryWarningSent(true);
            bookingRepository.save(booking);
        }
    }

    @Override
    @Transactional
    public void expireStaleBookings() {
        LocalDateTime expiryThreshold = LocalDateTime.now().minusMinutes(10);
        List<BookingStatus> expireableStatuses = List.of(
                BookingStatus.RESERVED,
                BookingStatus.GUEST_ADDED,
                BookingStatus.PAYMENT_PENDING
        );
        List<Booking> staleBookings = bookingRepository
                .findByBookingStatusInAndCreatedAtBefore(expireableStatuses, expiryThreshold);

        for (Booking booking : staleBookings) {
            inventoryRepository.findAndLockReservedInventory(
                    booking.getRoom().getId(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getRoomsCount()
            );
            inventoryRepository.cancelReservation(
                    booking.getRoom().getId(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getRoomsCount()
            );
            booking.setBookingStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);
            log.info("Expired stale booking with ID: {}", booking.getId());
        }
    }

}
