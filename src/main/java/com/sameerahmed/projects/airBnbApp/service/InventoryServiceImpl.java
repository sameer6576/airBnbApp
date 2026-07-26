package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.*;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.Inventory;
import com.sameerahmed.projects.airBnbApp.entity.Room;
import com.sameerahmed.projects.airBnbApp.entity.User;
import com.sameerahmed.projects.airBnbApp.exception.ResourceNotFoundException;
import com.sameerahmed.projects.airBnbApp.repository.HotelMinPriceRepository;
import com.sameerahmed.projects.airBnbApp.repository.InventoryRepository;
import com.sameerahmed.projects.airBnbApp.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    public Page<HotelPriceDto> searchHotels(HotelSearchRequest hotelSearchRequest) {
        log.info("Searching hotels for {} city, from {} to {}",
                hotelSearchRequest.getCity(), hotelSearchRequest.getStartDate(), hotelSearchRequest.getEndDate());
        Pageable pageable = PageRequest.of(hotelSearchRequest.getPage(), hotelSearchRequest.getSize());
        /*
         * Criteria to return a valid inventory
         * Return hotels with inventory which has
         * startDate <= date <= endDate
         * city
         * availability: (totalCount - bookedCount) >= roomsCount
         * closed = false
         *
         * group the response by room
         * and get the response by unique hotels
         * */
        Long dateCount = ChronoUnit.DAYS.between(hotelSearchRequest.getStartDate(), hotelSearchRequest.getEndDate()) + 1;
        Page<HotelPriceDto> hotelPage = hotelMinPriceRepository
                .findHotelWithAvailableInventory(
                        hotelSearchRequest.getCity(), hotelSearchRequest.getStartDate(),
                        hotelSearchRequest.getEndDate(), hotelSearchRequest.getRoomsCount(), dateCount, pageable);
        return hotelPage;
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
}
