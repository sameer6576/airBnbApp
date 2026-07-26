package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.HotelSearchRequest;
import com.sameerahmed.projects.airBnbApp.repository.HotelMinPriceRepository;
import com.sameerahmed.projects.airBnbApp.repository.InventoryRepository;
import com.sameerahmed.projects.airBnbApp.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceSearchTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private ModelMapper modelMapper;
    @Mock private HotelMinPriceRepository hotelMinPriceRepository;
    @Mock private RoomRepository roomRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    @Test
    void searchPassesAvailabilityParamsToRepository() {
        HotelSearchRequest request = new HotelSearchRequest();
        request.setCity("New York");
        request.setStartDate(LocalDate.of(2026, 8, 10));
        request.setEndDate(LocalDate.of(2026, 8, 12));
        request.setRoomsCount(2);
        request.setPage(0);
        request.setSize(10);

        when(hotelMinPriceRepository.findHotelWithAvailableInventory(
                eq("New York"),
                eq(LocalDate.of(2026, 8, 10)),
                eq(LocalDate.of(2026, 8, 12)),
                eq(2),
                eq(3L),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        Page<?> result = inventoryService.searchHotels(request);
        org.junit.jupiter.api.Assertions.assertTrue(result.isEmpty());

        verify(hotelMinPriceRepository).findHotelWithAvailableInventory(
                eq("New York"),
                eq(LocalDate.of(2026, 8, 10)),
                eq(LocalDate.of(2026, 8, 12)),
                eq(2),
                eq(3L),
                any(Pageable.class)
        );
    }
}
