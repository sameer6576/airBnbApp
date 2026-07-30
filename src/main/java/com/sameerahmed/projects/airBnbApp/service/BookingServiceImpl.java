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
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final CheckoutService checkoutService;
    private final PricingService pricingService;
    private final NotificationService notificationService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${app.cancellation.free-cancel-days:7}")
    private int freeCancelDays;

    @Value("${app.cancellation.partial-refund-percent:50}")
    private int partialRefundPercent;

    @Value("${app.booking.reservation-hold-minutes:10}")
    private int reservationHoldMinutes;

    @Value("${app.booking.payment-hold-minutes:30}")
    private int paymentHoldMinutes;

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
                    throw new IllegalArgumentException("Idempotency key mismatch");
                }

                // Replaying a key whose booking is already dead used to hand the dead
                // booking straight back, leaving the client permanently stuck on that
                // key with no way to retry. A key is only reusable while it still
                // refers to something the guest can act on.
                if (isTerminal(previous.getBookingStatus())) {
                    throw new IllegalStateException("Booking for this idempotency key is "
                            + previous.getBookingStatus() + "; retry with a new key");
                }

                log.info("Reusing booking {} for idempotency key", previous.getId());
                return modelMapper.map(previous, BookingDto.class);
            }

            try {
                return createNewBooking(bookingRequest, currentUser, scopedKey, fingerprint);
            } catch (DataIntegrityViolationException e) {
                // The read above and the insert are check-then-act; the unique index on
                // idempotencyKey is what actually serialises concurrent replays. The
                // loser gets a 409 rather than the catch-all 500.
                throw new IllegalStateException("A booking for this idempotency key is already being created");
            }
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

        // Inventory is keyed on the room, so a mismatched hotel id would otherwise
        // persist happily and then misattribute reports, analytics and reviews.
        if (!Objects.equals(room.getHotel().getId(), hotel.getId())) {
            throw new IllegalArgumentException("Room " + room.getId() + " does not belong to hotel " + hotel.getId());
        }

        List<Inventory> inventoryList = inventoryRepository
                .findAndLockAvailableInventory(bookingRequest.getRoomId(), bookingRequest.getCheckInDate(),
                        bookingRequest.getCheckOutDate(), bookingRequest.getRoomsCount());
        long nights = nightsCovered(bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate());
        if (inventoryList.size() != nights) {
            throw new IllegalStateException("Room is not available anymore");
        }

        int reserved = inventoryRepository.initBooking(bookingRequest.getRoomId(), bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate(), bookingRequest.getRoomsCount());
        if (reserved != nights) {
            throw new IllegalStateException("Room is not available anymore");
        }

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
                .holdExpiresAt(LocalDateTime.now().plusMinutes(reservationHoldMinutes))
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

        if (booking.getBookingStatus() != BookingStatus.RESERVED
                && booking.getBookingStatus() != BookingStatus.GUEST_ADDED
                && booking.getBookingStatus() != BookingStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Guests cannot be added in status " + booking.getBookingStatus());
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
        if (!guestDtoList.isEmpty() && booking.getBookingStatus() == BookingStatus.RESERVED) {
            booking.setBookingStatus(BookingStatus.GUEST_ADDED);
        }
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

        if (booking.getBookingStatus() != BookingStatus.RESERVED
                && booking.getBookingStatus() != BookingStatus.GUEST_ADDED
                && booking.getBookingStatus() != BookingStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("Payment cannot be started in status " + booking.getBookingStatus());
        }

        // The hold is extended before the session is created so that the session
        // can be pinned to the same deadline. Otherwise Stripe's default 24-hour
        // session outlives a 30-minute hold by nearly a day, which is what allows
        // a payment to arrive for inventory that has already been released.
        booking.setBookingStatus(BookingStatus.PAYMENT_PENDING);
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(paymentHoldMinutes));
        booking.setExpiryWarningSent(false);
        bookingRepository.save(booking);

        CheckoutService.CheckoutSession session = checkoutService.getCheckoutSession(booking,
                frontendUrl + "/payments/success", frontendUrl + "/payments/failure");

        booking.setPaymentSessionId(session.id());
        bookingRepository.save(booking);

        return session.url();
    }

    @Override
    @Transactional
    public void capturePayment(Event event) {
        if (processedStripeEventRepository.existsById(event.getId())) {
            log.info("Ignoring Stripe event {} because it was already processed", event.getId());
            return;
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> onCheckoutCompleted(event);
            case "checkout.session.expired" -> onCheckoutExpired(event);
            default -> {
                log.warn("Unhandled event type: {}", event.getType());
                return;
            }
        }

        processedStripeEventRepository.save(new ProcessedStripeEvent(event.getId(), event.getType()));
    }

    private void onCheckoutCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session == null) return;

        Booking booking = findBookingForSession(session);

        if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            log.info("Booking already confirmed for session id: {}", session.getId());
            return;
        }

        assertPaidAmountMatches(booking, session);

        switch (booking.getBookingStatus()) {
            // The hold died before the money arrived, so the reservation has
            // already been released. Try to take the rooms back; refund if we
            // cannot. Confirming regardless would charge for nothing.
            case EXPIRED -> settleLatePayment(booking);

            // The guest cancelled and paid anyway. Their intent was to not book,
            // so never re-acquire — just return the money.
            case CANCELLED -> refundInFull(booking, "payment received for a cancelled booking");

            case REFUNDED -> log.warn("Ignoring payment for already refunded booking {}", booking.getId());

            // PAYMENT_PENDING is the normal path. RESERVED and GUEST_ADDED should
            // be unreachable, since only initiatePayments assigns a session id,
            // but the money is real and the hold is live, so confirm anyway.
            default -> confirmPaidBooking(booking);
        }
    }

    /**
     * Resolves the booking a Checkout Session belongs to, falling back to the
     * client reference id we set at session creation. Without that fallback, a
     * failure to persist the session id would leave a captured payment with no
     * booking the webhook could ever match.
     */
    private Booking findBookingForSession(Session session) {
        Optional<Booking> bySessionId = bookingRepository.findByPaymentSessionId(session.getId());
        if (bySessionId.isPresent()) {
            return bySessionId.get();
        }

        String clientReferenceId = session.getClientReferenceId();
        if (clientReferenceId != null) {
            try {
                Optional<Booking> byReference = bookingRepository.findById(Long.valueOf(clientReferenceId));
                if (byReference.isPresent()) {
                    log.warn("Booking {} resolved by client reference; its payment session id was never stored",
                            clientReferenceId);
                    return byReference.get();
                }
            } catch (NumberFormatException e) {
                log.warn("Ignoring non-numeric client reference id {}", clientReferenceId);
            }
        }

        throw new ResourceNotFoundException("Booking not found for session id: " + session.getId());
    }

    private void onCheckoutExpired(Event event) {
        Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session == null) return;

        Optional<Booking> found = bookingRepository.findByPaymentSessionId(session.getId());
        if (found.isEmpty()) return;

        Booking booking = found.get();
        if (booking.getBookingStatus() != BookingStatus.PAYMENT_PENDING) {
            return;
        }

        // Releasing here frees the rooms as soon as Stripe gives up on the
        // session, rather than leaving them held until the expiry job next runs.
        lockInventoryRange(booking.getRoom().getId(), booking.getCheckInDate(), booking.getCheckOutDate());
        int released = inventoryRepository.cancelReservation(booking.getRoom().getId(), booking.getCheckInDate(),
                booking.getCheckOutDate(), booking.getRoomsCount());
        assertAllNightsUpdated(released, booking.getCheckInDate(), booking.getCheckOutDate(), "cancelReservation");

        booking.setBookingStatus(BookingStatus.EXPIRED);
        bookingRepository.save(booking);
        log.info("Released hold for booking {} after its checkout session expired", booking.getId());
    }

    private void confirmPaidBooking(Booking booking) {
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        lockInventoryRange(booking.getRoom().getId(), booking.getCheckInDate(), booking.getCheckOutDate());

        int confirmed = inventoryRepository.confirmBooking(booking.getRoom().getId(), booking.getCheckInDate(),
                booking.getCheckOutDate(), booking.getRoomsCount());
        assertAllNightsUpdated(confirmed, booking.getCheckInDate(), booking.getCheckOutDate(), "confirmBooking");

        notificationService.sendBookingConfirmed(booking);
        log.info("Successfully confirmed the booking for Booking ID: {}", booking.getId());
    }

    /**
     * Re-acquires inventory for a booking whose hold lapsed before payment landed.
     * Availability is checked under a pessimistic lock first so that the following
     * reserve cannot partially succeed — a partial reserve would have to be
     * unwound, and there is no clean way to do that alongside a refund.
     */
    private void settleLatePayment(Booking booking) {
        Long roomId = booking.getRoom().getId();
        LocalDate checkIn = booking.getCheckInDate();
        LocalDate checkOut = booking.getCheckOutDate();
        long nights = nightsCovered(checkIn, checkOut);

        List<Inventory> stillAvailable = inventoryRepository.findAndLockAvailableInventory(
                roomId, checkIn, checkOut, booking.getRoomsCount());

        if (stillAvailable.size() != nights) {
            log.warn("Booking {} was paid after its hold expired and the rooms are gone; refunding", booking.getId());
            refundInFull(booking, "rooms no longer available");
            return;
        }

        assertAllNightsUpdated(
                inventoryRepository.initBooking(roomId, checkIn, checkOut, booking.getRoomsCount()),
                checkIn, checkOut, "initBooking");

        booking.setHoldExpiresAt(null);
        confirmPaidBooking(booking);
        log.info("Recovered booking {} after a late payment", booking.getId());
    }

    /**
     * Throwing on a Stripe failure is deliberate: it rolls back the inventory
     * release and the status change, so a booking is never marked refunded when
     * the money did not actually move.
     */
    private void issueRefund(Booking booking, BigDecimal refundAmount) {
        if (refundAmount == null
                || refundAmount.compareTo(BigDecimal.ZERO) <= 0
                || booking.getPaymentSessionId() == null) {
            return;
        }
        try {
            Session session = Session.retrieve(booking.getPaymentSessionId());
            RefundCreateParams.Builder refundBuilder = RefundCreateParams.builder()
                    .setPaymentIntent(session.getPaymentIntent());
            if (refundAmount.compareTo(booking.getAmount()) < 0) {
                long cents = refundAmount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
                refundBuilder.setAmount(cents);
            }
            // Scoped to the booking so a redelivered webhook or a retried cancel
            // cannot refund the same booking twice.
            Refund.create(refundBuilder.build(),
                    com.stripe.net.RequestOptions.builder()
                            .setIdempotencyKey("refund-booking-" + booking.getId())
                            .build());
        } catch (StripeException e) {
            throw new IllegalStateException("Refund failed: " + e.getMessage());
        }
    }

    private void refundInFull(Booking booking, String reason) {
        BigDecimal amount = booking.getAmount();
        issueRefund(booking, amount);

        booking.setBookingStatus(BookingStatus.REFUNDED);
        booking.setRefundAmount(amount);
        bookingRepository.save(booking);

        notificationService.sendBookingCancelled(booking, amount);
        log.info("Refunded booking {} in full ({}): {}", booking.getId(), amount, reason);
    }

    /**
     * A mismatch should be impossible: the amount is fixed at init and, now that
     * date changes are restricted to unpaid holds, nothing can move it while a
     * session is open. So this failing means tampering or a bug, and rolling back
     * loudly is better than confirming a booking for the wrong money.
     */
    private void assertPaidAmountMatches(Booking booking, Session session) {
        Long paidInCents = session.getAmountTotal();
        if (paidInCents == null) {
            return;
        }
        long expectedInCents = booking.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        if (paidInCents != expectedInCents) {
            log.error("Booking {} expected {} cents but Stripe reported {}",
                    booking.getId(), expectedInCents, paidInCents);
            throw new IllegalStateException("Paid amount does not match booking " + booking.getId());
        }
    }

    @Override
    @Transactional
    public void cancelPayment(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        User user = getCurrentUser();
        assertBookingOwner(booking, user);

        if (isUnpaidHold(booking.getBookingStatus())) {
            cancelUnpaidBooking(booking);
            return;
        }

        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Booking cannot be cancelled in status " + booking.getBookingStatus());
        }

        CancellationQuoteDto quote = buildCancellationQuote(booking);
        BigDecimal refundAmount = quote.getEstimatedRefund();

        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setRefundAmount(refundAmount);
        bookingRepository.save(booking);

        lockInventoryRange(booking.getRoom().getId(), booking.getCheckInDate(), booking.getCheckOutDate());

        int released = inventoryRepository.cancelBooking(booking.getRoom().getId(), booking.getCheckInDate(),
                booking.getCheckOutDate(), booking.getRoomsCount());
        assertAllNightsUpdated(released, booking.getCheckInDate(), booking.getCheckOutDate(), "cancelBooking");

        issueRefund(booking, refundAmount);

        notificationService.sendBookingCancelled(booking, refundAmount);
        log.info("Successfully cancelled booking {} with refund {}", booking.getId(), refundAmount);
    }

    private void cancelUnpaidBooking(Booking booking) {
        lockInventoryRange(booking.getRoom().getId(), booking.getCheckInDate(), booking.getCheckOutDate());
        int released = inventoryRepository.cancelReservation(
                booking.getRoom().getId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getRoomsCount()
        );
        assertAllNightsUpdated(released, booking.getCheckInDate(), booking.getCheckOutDate(), "cancelReservation");
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setRefundAmount(BigDecimal.ZERO);
        bookingRepository.save(booking);
        notificationService.sendBookingCancelled(booking, BigDecimal.ZERO);
        log.info("Cancelled unpaid booking {}", booking.getId());
    }

    private boolean isUnpaidHold(BookingStatus status) {
        return status == BookingStatus.RESERVED
                || status == BookingStatus.GUEST_ADDED
                || status == BookingStatus.PAYMENT_PENDING;
    }

    @Override
    public CancellationQuoteDto getCancellationQuote(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        assertBookingOwner(booking, getCurrentUser());
        if (isUnpaidHold(booking.getBookingStatus())) {
            return new CancellationQuoteDto(true, 0, freeCancelDays, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Cancellation quote is only available for unpaid or confirmed bookings");
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

        // Unpaid holds only. Changing dates once money is involved also changes the
        // amount, and there is no charge-or-refund-the-difference flow: a
        // PAYMENT_PENDING booking has an open Checkout session still quoting the
        // old price, and a CONFIRMED one would silently reprice with no settlement.
        if (booking.getBookingStatus() != BookingStatus.RESERVED
                && booking.getBookingStatus() != BookingStatus.GUEST_ADDED) {
            throw new IllegalStateException("Dates can only be changed before payment starts, not in status "
                    + booking.getBookingStatus());
        }
        if (hasBookingExpired(booking)) {
            throw new IllegalStateException("Booking has already expired");
        }

        Long roomId = booking.getRoom().getId();
        LocalDate oldIn = booking.getCheckInDate();
        LocalDate oldOut = booking.getCheckOutDate();
        int oldCount = booking.getRoomsCount();

        List<Inventory> inventoryList = inventoryRepository.findAndLockAvailableInventory(
                roomId, request.getCheckInDate(), request.getCheckOutDate(), request.getRoomsCount());
        if (inventoryList.size() != nightsCovered(request.getCheckInDate(), request.getCheckOutDate())) {
            throw new IllegalStateException("Room is not available for the new dates");
        }

        // Both ranges are locked up front, ordered by check-in date. Acquiring them
        // in a data-dependent order is what lets two users swapping date ranges
        // deadlock against each other.
        LocalDate newIn = request.getCheckInDate();
        LocalDate newOut = request.getCheckOutDate();
        if (oldIn.isAfter(newIn)) {
            lockInventoryRange(roomId, newIn, newOut);
            lockInventoryRange(roomId, oldIn, oldOut);
        } else {
            lockInventoryRange(roomId, oldIn, oldOut);
            lockInventoryRange(roomId, newIn, newOut);
        }

        assertAllNightsUpdated(inventoryRepository.cancelReservation(roomId, oldIn, oldOut, oldCount),
                oldIn, oldOut, "cancelReservation");
        assertAllNightsUpdated(inventoryRepository.initBooking(roomId, newIn, newOut, request.getRoomsCount()),
                newIn, newOut, "initBooking");

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
                && booking.getBookingStatus() != BookingStatus.PAYMENT_PENDING
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

        // The reporting window is inclusive because it filters on booking createdAt,
        // not on stay dates — "the 1st to the 5th" means five days of activity.
        long inventoryDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        int totalRoomUnits = hotel.getRooms() == null ? 0 :
                hotel.getRooms().stream().mapToInt(Room::getTotalCount).sum();
        long capacityNights = Math.max(inventoryDays, 1) * Math.max(totalRoomUnits, 1);
        long bookedNights = bookings.stream()
                .filter(b -> b.getBookingStatus() == BookingStatus.CONFIRMED)
                .mapToLong(b -> nightsCovered(b.getCheckInDate(), b.getCheckOutDate()) * b.getRoomsCount())
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
        if (booking.getBookingStatus() == BookingStatus.CONFIRMED
                || booking.getBookingStatus() == BookingStatus.CANCELLED
                || booking.getBookingStatus() == BookingStatus.EXPIRED) {
            return false;
        }
        LocalDateTime deadline = booking.getHoldExpiresAt();
        if (deadline == null && booking.getCreatedAt() != null) {
            deadline = booking.getCreatedAt().plusMinutes(reservationHoldMinutes);
        }
        return deadline != null && deadline.isBefore(LocalDateTime.now());
    }

    private void assertBookingOwner(Booking booking, User user) {
        if (!Objects.equals(user.getId(), booking.getUser().getId())) {
            throw new AccessDeniedException("Booking does not belong to this user with id: " + user.getId());
        }
    }

    /**
     * The single definition of how many inventory rows a stay spans. Every
     * availability check, row-count assertion and price sum derives from this, so
     * the check-in/check-out convention is changed here and nowhere else.
     */
    /** Statuses a booking cannot leave, so no further action is possible on it. */
    private boolean isTerminal(BookingStatus status) {
        return status == BookingStatus.CANCELLED
                || status == BookingStatus.EXPIRED
                || status == BookingStatus.REFUNDED;
    }

    private long nightsCovered(LocalDate checkInDate, LocalDate checkOutDate) {
        return ChronoUnit.DAYS.between(checkInDate, checkOutDate);
    }

    /**
     * Takes row locks across the booking's whole date range, unfiltered. Locking
     * is not the place to enforce availability: a predicate here would silently
     * exclude the very rows the following update needs to hold, leaving that
     * update to run unlocked.
     */
    private void lockInventoryRange(Long roomId, LocalDate checkInDate, LocalDate checkOutDate) {
        long expected = nightsCovered(checkInDate, checkOutDate);
        List<Inventory> locked = inventoryRepository.lockStayRange(roomId, checkInDate, checkOutDate);
        if (locked.size() != expected) {
            throw new IllegalStateException("Expected " + expected + " inventory rows for room " + roomId
                    + " between " + checkInDate + " and " + checkOutDate + " but found " + locked.size());
        }
    }

    /**
     * Turns a partial bulk update into a rollback. Without this the guards on
     * those statements fail open: a night that did not match is simply skipped.
     */
    private void assertAllNightsUpdated(int rowsUpdated, LocalDate checkInDate, LocalDate checkOutDate, String operation) {
        long expected = nightsCovered(checkInDate, checkOutDate);
        if (rowsUpdated != expected) {
            throw new IllegalStateException(operation + " affected " + rowsUpdated
                    + " of " + expected + " nights between " + checkInDate + " and " + checkOutDate);
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
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warnUntil = now.plusMinutes(2);
        List<BookingStatus> statuses = List.of(
                BookingStatus.RESERVED,
                BookingStatus.GUEST_ADDED,
                BookingStatus.PAYMENT_PENDING
        );
        List<Booking> soonToExpire = bookingRepository
                .findByBookingStatusInAndExpiryWarningSentFalseAndHoldExpiresAtBetween(statuses, now, warnUntil);
        for (Booking booking : soonToExpire) {
            notificationService.sendBookingExpiryWarning(booking);
            booking.setExpiryWarningSent(true);
            bookingRepository.save(booking);
        }
    }

    @Override
    @Transactional
    public void expireStaleBookings() {
        List<BookingStatus> expireableStatuses = List.of(
                BookingStatus.RESERVED,
                BookingStatus.GUEST_ADDED,
                BookingStatus.PAYMENT_PENDING
        );
        LocalDateTime now = LocalDateTime.now();
        List<Booking> staleBookings = bookingRepository.findExpiredHolds(
                expireableStatuses, now, now.minusMinutes(reservationHoldMinutes));

        for (Booking booking : staleBookings) {
            lockInventoryRange(booking.getRoom().getId(), booking.getCheckInDate(), booking.getCheckOutDate());
            int released = inventoryRepository.cancelReservation(
                    booking.getRoom().getId(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    booking.getRoomsCount()
            );

            // Unlike the interactive paths, a mismatch here must not throw: this
            // loop processes a whole batch, and one inconsistent booking should
            // not prevent every other hold from being released. A zero release is
            // also the expected outcome if the reservation was already freed.
            long nights = nightsCovered(booking.getCheckInDate(), booking.getCheckOutDate());
            if (released != nights) {
                log.warn("Expiring booking {}: released {} of {} nights", booking.getId(), released, nights);
            }

            booking.setBookingStatus(BookingStatus.EXPIRED);
            bookingRepository.save(booking);
            log.info("Expired stale booking with ID: {}", booking.getId());
        }
    }

}
