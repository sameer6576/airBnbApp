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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
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

    @Override
    public void initializeRoomForAYear(Room room) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusYears(1);
        for (; !today.isAfter(endDate); today = today.plusDays(1)) {
            Inventory inventory = Inventory.builder()
                    .hotel(room.getHotel())
                    .room(room)
                    .bookedCount(0)
                    .reservedCount(0)
                    .city(room.getHotel().getCity())
                    .date(today)
                    .price(room.getBasePrice())
                    .surgeFactor(BigDecimal.ONE)
                    .totalCount(room.getTotalCount())
                    .closed(false)
                    .build();
            inventoryRepository.save(inventory);
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

        Long dateCount = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
        List<HotelPriceDto> results = hotelMinPriceRepository.findHotelWithAvailableInventory(
                request.getCity(),
                request.getStartDate(),
                request.getEndDate(),
                request.getRoomsCount(),
                dateCount,
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
        int from = Math.min(page * size, results.size());
        int to = Math.min(from + size, results.size());
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
    }

    @Override
    @Transactional
    public void syncFutureInventoryForRoom(Room room) {
        log.info("Syncing future inventory for room ID: {}", room.getId());
        inventoryRepository.updateFutureInventoryForRoom(
                room.getId(),
                LocalDate.now(),
                room.getBasePrice(),
                room.getTotalCount()
        );
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
        log.info("Bulk updated inventory for hotel {} between {} and {}",
                hotelId, request.getStartDate(), request.getEndDate());
    }
}
