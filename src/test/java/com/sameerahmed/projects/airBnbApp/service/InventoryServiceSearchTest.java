package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.HotelSearchRequest;
import com.sameerahmed.projects.airBnbApp.repository.HotelMinPriceRepository;
import com.sameerahmed.projects.airBnbApp.repository.HotelRepository;
import com.sameerahmed.projects.airBnbApp.repository.InventoryRepository;
import com.sameerahmed.projects.airBnbApp.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceSearchTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private ModelMapper modelMapper;
    @Mock private HotelMinPriceRepository hotelMinPriceRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private HotelRepository hotelRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    @Test
    void searchPassesAvailabilityAndFilterParamsToRepository() {
        HotelSearchRequest request = new HotelSearchRequest();
        request.setCity("New York");
        request.setStartDate(LocalDate.of(2026, 8, 10));
        request.setEndDate(LocalDate.of(2026, 8, 12));
        request.setRoomsCount(2);
        request.setMinPrice(BigDecimal.valueOf(50));
        request.setMaxPrice(BigDecimal.valueOf(300));
        request.setMinRating(4.0);
        request.setMinCapacity(2);
        request.setPage(0);
        request.setSize(10);

        when(hotelMinPriceRepository.findHotelWithAvailableInventory(
                eq("New York"),
                eq(LocalDate.of(2026, 8, 10)),
                eq(LocalDate.of(2026, 8, 12)),
                eq(2),
                eq(3L),
                eq(BigDecimal.valueOf(50)),
                eq(BigDecimal.valueOf(300)),
                eq(4.0),
                eq(2)
        )).thenReturn(List.of());

        Page<?> result = inventoryService.searchHotels(request);
        org.junit.jupiter.api.Assertions.assertTrue(result.isEmpty());

        verify(hotelMinPriceRepository).findHotelWithAvailableInventory(
                eq("New York"),
                eq(LocalDate.of(2026, 8, 10)),
                eq(LocalDate.of(2026, 8, 12)),
                eq(2),
                eq(3L),
                eq(BigDecimal.valueOf(50)),
                eq(BigDecimal.valueOf(300)),
                eq(4.0),
                eq(2)
        );
    }
}
