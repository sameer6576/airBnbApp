package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.*;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.Inventory;
import com.sameerahmed.projects.airBnbApp.entity.Room;
import com.sameerahmed.projects.airBnbApp.entity.User;
import com.sameerahmed.projects.airBnbApp.exception.ResourceNotFoundException;
import com.sameerahmed.projects.airBnbApp.repository.HotelMinPriceRepository;
import com.sameerahmed.projects.airBnbApp.repository.HotelRepository;
import com.sameerahmed.projects.airBnbApp.repository.InventoryRepository;
import com.sameerahmed.projects.airBnbApp.repository.RoomRepository;
import com.sameerahmed.projects.airBnbApp.strategy.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sameerahmed.projects.airBnbApp.util.AppUtils.getCurrentUser;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ModelMapper modelMapper;
    private final HotelMinPriceRepository hotelMinPriceRepository;
    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final HotelMinPriceService hotelMinPriceService;
    private final PricingService pricingService;

    /**
     * How far ahead inventory is generated. The roll-forward job keeps this many
     * days populated, so the bookable window stays constant instead of decaying.
     */
    @Value("${app.inventory.horizon-days:365}")
    private int horizonDays;

    @Override
    @Transactional
    public int ensureInventoryHorizon(Room room) {
        Hotel hotel = room.getHotel();
        LocalDate today = LocalDate.now();
        LocalDate horizonEnd = today.plusDays(horizonDays);

        // Generating only the dates that are missing is what makes this safe to
        // call repeatedly. The previous version always generated a fixed year from
        // today, so a second call collided with the (hotel_id, room_id, date)
        // unique constraint, and nothing ever extended the horizon afterwards —
        // the bookable window shrank by a day every day.
        Set<LocalDate> existing = new HashSet<>(
                inventoryRepository.findExistingDates(room.getId(), today, horizonEnd));

        List<Inventory> inventories = new ArrayList<>();
        for (LocalDate date = today; !date.isAfter(horizonEnd); date = date.plusDays(1)) {
            if (existing.contains(date)) {
                continue;
            }
            inventories.add(
                    Inventory.builder()
                            .hotel(hotel)
                            .room(room)
                            .city(hotel.getCity())
                            .date(date)
                            .price(room.getBasePrice())
                            .totalCount(room.getTotalCount())
                            .bookedCount(0)
                            .reservedCount(0)
                            .surgeFactor(BigDecimal.ONE)
                            .closed(false)
                            .build()
            );
        }

        if (inventories.isEmpty()) {
            return 0;
        }

        inventoryRepository.saveAll(inventories);
        log.debug("Created {} inventory rows for room {}", inventories.size(), room.getId());
        return inventories.size();
    }

    @Override
    @Transactional
    public void refreshHotelInventory(Hotel hotel) {
        // Rooms are re-read rather than taken from hotel.getRooms(), which can be a
        // stale collection when a room was just added in this transaction.
        List<Room> rooms = roomRepository.findByHotel(hotel);

        int created = 0;
        for (Room room : rooms) {
            created += ensureInventoryHorizon(room);
        }

        // Once per hotel, not once per room: updateHotelMinPrice deletes and
        // reinserts a year of rows, so doing it per room made an N-room hotel do N
        // full passes over the same table.
        if (created > 0) {
            hotelMinPriceService.updateHotelMinPrice(hotel.getId());
            log.info("Extended inventory for hotel {} by {} rows", hotel.getId(), created);
        }
    }

    @Override
    public void deleteAllInventories(Room room) {
        inventoryRepository.deleteByRoom(room);
    }

    @Override
    public Page<HotelPriceDto> searchHotels(HotelSearchRequest request) {
        log.info("Searching hotels for {} city, from {} to {}, filters: minPrice={}, maxPrice={}, minRating={}, minCapacity={}, amenities={}, sort={}",
                request.getCity(), request.getStartDate(), request.getEndDate(),
                request.getMinPrice(), request.getMaxPrice(), request.getMinRating(),
                request.getMinCapacity(), request.getAmenities(), request.getSortBy());

        // Nights, not calendar days: the check-out date is not occupied. This must
        // agree with BookingServiceImpl.nightsCovered, or search and booking will
        // disagree about whether a stay is available.
        Long nightCount = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
        List<HotelPriceDto> results = hotelMinPriceRepository.findHotelWithAvailableInventory(
                request.getCity(),
                request.getStartDate(),
                request.getEndDate(),
                request.getRoomsCount(),
                nightCount,
                request.getMinPrice(),
                request.getMaxPrice(),
                request.getMinRating(),
                request.getMinCapacity()
        );

        List<String> requiredAmenities = request.getAmenities() == null ? List.of() :
                request.getAmenities().stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.trim().toLowerCase())
                        .filter(a -> !a.isEmpty())
                        .toList();

        if (!requiredAmenities.isEmpty()) {
            results = results.stream()
                    .filter(dto -> hotelHasAllAmenities(dto.getHotel(), requiredAmenities))
                    .collect(Collectors.toList());
        }

        HotelSearchRequest.SearchSort sortBy = request.getSortBy() != null
                ? request.getSortBy()
                : HotelSearchRequest.SearchSort.PRICE_ASC;

        results = results.stream()
                .sorted((a, b) -> compareSearchResults(a, b, sortBy))
                .collect(Collectors.toList());

        int page = request.getPage() == null ? 0 : request.getPage();
        int size = request.getSize() == null ? 10 : request.getSize();
        // long arithmetic: page * size overflowed int for large pages, went negative,
        // and subList then threw IndexOutOfBoundsException as a 500.
        int from = (int) Math.min((long) page * size, results.size());
        int to = (int) Math.min((long) from + size, results.size());
        List<HotelPriceDto> pageContent = results.subList(from, to);

        return new org.springframework.data.domain.PageImpl<>(
                pageContent,
                PageRequest.of(page, size),
                results.size()
        );
    }

    private boolean hotelHasAllAmenities(HotelDto hotel, List<String> requiredAmenities) {
        if (hotel == null || hotel.getAmenities() == null) {
            return false;
        }
        List<String> hotelAmenities = java.util.Arrays.stream(hotel.getAmenities())
                .filter(Objects::nonNull)
                .map(a -> a.trim().toLowerCase())
                .toList();
        return requiredAmenities.stream().allMatch(hotelAmenities::contains);
    }

    private int compareSearchResults(HotelPriceDto a, HotelPriceDto b, HotelSearchRequest.SearchSort sortBy) {
        return switch (sortBy) {
            case PRICE_DESC -> Double.compare(
                    b.getPrice() != null ? b.getPrice() : 0,
                    a.getPrice() != null ? a.getPrice() : 0);
            case RATING_DESC -> Double.compare(
                    b.getAverageRating() != null ? b.getAverageRating() : 0,
                    a.getAverageRating() != null ? a.getAverageRating() : 0);
            case PRICE_ASC -> Double.compare(
                    a.getPrice() != null ? a.getPrice() : 0,
                    b.getPrice() != null ? b.getPrice() : 0);
        };
    }

    @Override
    public List<InventoryDto> getAllInventoryByRoom(Long roomId) {

        log.info("Getting all the inventory for room with roomID: {}", roomId);
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + roomId));

        User user = getCurrentUser();

        if (!Objects.equals(user.getId(), room.getHotel().getOwner().getId())) {
            throw new AccessDeniedException("You are not the owner of the room with ID: " + roomId);
        }

        List<Inventory> inventoryList = inventoryRepository.findByRoomOrderByDate(room);

        return inventoryList
                .stream()
                .map((element) -> modelMapper.map(element, InventoryDto.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateInventory(Long roomId, UpdateInventoryRequestDto updateInventoryRequestDto) {
        log.info("Getting all the inventory for room with roomID: {} between date range: {} - {}", roomId, updateInventoryRequestDto.getStartDate(), updateInventoryRequestDto.getEndDate());

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + roomId));

        User user = getCurrentUser();

        if (!Objects.equals(user.getId(), room.getHotel().getOwner().getId())) {
            throw new AccessDeniedException("You are not the owner of the room with ID: " + roomId);
        }
        inventoryRepository.getInventoryAndLockBeforeUpdate(roomId,
                updateInventoryRequestDto.getStartDate(),
                updateInventoryRequestDto.getEndDate());

        inventoryRepository.updateInventory(roomId,
                updateInventoryRequestDto.getStartDate(),
                updateInventoryRequestDto.getEndDate(),
                updateInventoryRequestDto.getClosed(),
                updateInventoryRequestDto.getSurgeFactor());
        hotelMinPriceService.updateHotelMinPrice(room.getHotel().getId());
    }

    @Override
    @Transactional
    public void syncFutureInventoryForRoom(Room room) {
        log.info("Syncing future inventory for room ID: {}", room.getId());

        List<Inventory> future = inventoryRepository.findByRoomAndDateGreaterThanEqual(room, LocalDate.now());
        int newTotalCount = room.getTotalCount();

        // Validate before mutating anything. Lowering totalCount below what is
        // already sold or held would oversell those nights, and nothing checked for
        // it: availability is totalCount - bookedCount - reservedCount, so the
        // shortfall simply became negative availability.
        List<LocalDate> oversold = future.stream()
                .filter(i -> newTotalCount < i.getBookedCount() + i.getReservedCount())
                .map(Inventory::getDate)
                .limit(5)
                .toList();

        if (!oversold.isEmpty()) {
            throw new IllegalStateException("Cannot reduce room " + room.getId() + " to " + newTotalCount
                    + " units: more are already booked or held on " + oversold);
        }

        for (Inventory inventory : future) {
            inventory.setTotalCount(newTotalCount);
            // Recompute rather than writing the raw base price. This column is what
            // search reads and what a booking is charged, so overwriting it with the
            // base price silently discarded surge, occupancy, urgency and holiday.
            inventory.setPrice(pricingService.calculateDynamicPricing(inventory));
        }

        inventoryRepository.saveAll(future);
        hotelMinPriceService.updateHotelMinPrice(room.getHotel().getId());
    }

    @Override
    @Transactional
    public void bulkUpdateInventoryForHotel(Long hotelId, BulkInventoryUpdateRequest request) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));
        User user = getCurrentUser();
        if (!Objects.equals(user.getId(), hotel.getOwner().getId())) {
            throw new AccessDeniedException("You are not the owner of the hotel with ID: " + hotelId);
        }
        List<Room> rooms = hotel.getRooms() == null ? List.of() : hotel.getRooms();
        for (Room room : rooms) {
            inventoryRepository.getInventoryAndLockBeforeUpdate(
                    room.getId(), request.getStartDate(), request.getEndDate());
            inventoryRepository.updateInventory(
                    room.getId(),
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getClosed(),
                    request.getSurgeFactor()
            );
        }
        hotelMinPriceService.updateHotelMinPrice(hotelId);
        log.info("Bulk updated inventory for hotel {} between {} and {}",
                hotelId, request.getStartDate(), request.getEndDate());
    }
}
