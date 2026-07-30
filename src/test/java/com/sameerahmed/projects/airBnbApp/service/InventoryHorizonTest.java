package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.Inventory;
import com.sameerahmed.projects.airBnbApp.entity.Room;
import com.sameerahmed.projects.airBnbApp.repository.HotelMinPriceRepository;
import com.sameerahmed.projects.airBnbApp.repository.HotelRepository;
import com.sameerahmed.projects.airBnbApp.repository.InventoryRepository;
import com.sameerahmed.projects.airBnbApp.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The horizon used to decay: inventory was generated once, relative to "today",
 * and never extended. These tests pin the two properties that fix it — generation
 * is idempotent, and re-running it rolls the window forward.
 */
@ExtendWith(MockitoExtension.class)
class InventoryHorizonTest {

    private static final int HORIZON_DAYS = 10;

    @Mock private InventoryRepository inventoryRepository;
    @Mock private ModelMapper modelMapper;
    @Mock private HotelMinPriceRepository hotelMinPriceRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private HotelMinPriceService hotelMinPriceService;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private Hotel hotel;
    private Room room;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inventoryService, "horizonDays", HORIZON_DAYS);

        hotel = new Hotel();
        hotel.setId(3L);
        hotel.setCity("Paris");

        room = new Room();
        room.setId(9L);
        room.setHotel(hotel);
        room.setBasePrice(BigDecimal.valueOf(150));
        room.setTotalCount(4);
    }

    @Test
    void generatesTheWholeHorizonWhenRoomHasNoInventory() {
        when(inventoryRepository.findExistingDates(eq(9L), any(), any())).thenReturn(List.of());

        int created = inventoryService.ensureInventoryHorizon(room);

        // Today through today+horizon inclusive.
        assertEquals(HORIZON_DAYS + 1, created);
        assertEquals(HORIZON_DAYS + 1, capturedRows().size());
    }

    @Test
    void createsNothingWhenHorizonIsAlreadyFull() {
        LocalDate today = LocalDate.now();
        List<LocalDate> everyDate = new ArrayList<>();
        for (int i = 0; i <= HORIZON_DAYS; i++) {
            everyDate.add(today.plusDays(i));
        }
        when(inventoryRepository.findExistingDates(eq(9L), any(), any())).thenReturn(everyDate);

        int created = inventoryService.ensureInventoryHorizon(room);

        assertEquals(0, created);
        verify(inventoryRepository, never()).saveAll(any());
    }

    /**
     * The roll-forward case: a day has passed since generation, so exactly one new
     * date at the far end of the window is missing.
     */
    @Test
    void addsOnlyTheNewlyUncoveredDayAsTimePasses() {
        LocalDate today = LocalDate.now();
        List<LocalDate> allButLast = new ArrayList<>();
        for (int i = 0; i < HORIZON_DAYS; i++) {
            allButLast.add(today.plusDays(i));
        }
        when(inventoryRepository.findExistingDates(eq(9L), any(), any())).thenReturn(allButLast);

        int created = inventoryService.ensureInventoryHorizon(room);

        assertEquals(1, created);
        assertEquals(today.plusDays(HORIZON_DAYS), capturedRows().getFirst().getDate());
    }

    /** Gaps left by a missed run are filled rather than assumed contiguous. */
    @Test
    void fillsGapsInTheMiddleOfTheWindow() {
        LocalDate today = LocalDate.now();
        List<LocalDate> withGaps = new ArrayList<>();
        for (int i = 0; i <= HORIZON_DAYS; i++) {
            if (i != 3 && i != 7) {
                withGaps.add(today.plusDays(i));
            }
        }
        when(inventoryRepository.findExistingDates(eq(9L), any(), any())).thenReturn(withGaps);

        int created = inventoryService.ensureInventoryHorizon(room);

        assertEquals(2, created);
        List<LocalDate> dates = capturedRows().stream().map(Inventory::getDate).toList();
        assertEquals(List.of(today.plusDays(3), today.plusDays(7)), dates);
    }

    @Test
    void hotelRefreshUpdatesMinPriceOncePerHotelNotOncePerRoom() {
        Room second = new Room();
        second.setId(10L);
        second.setHotel(hotel);
        second.setBasePrice(BigDecimal.valueOf(90));
        second.setTotalCount(2);

        when(roomRepository.findByHotel(hotel)).thenReturn(List.of(room, second));
        when(inventoryRepository.findExistingDates(anyLong(), any(), any())).thenReturn(List.of());

        inventoryService.refreshHotelInventory(hotel);

        verify(hotelMinPriceService).updateHotelMinPrice(3L);
    }

    @Test
    void hotelRefreshSkipsMinPriceWhenNothingWasCreated() {
        LocalDate today = LocalDate.now();
        List<LocalDate> everyDate = new ArrayList<>();
        for (int i = 0; i <= HORIZON_DAYS; i++) {
            everyDate.add(today.plusDays(i));
        }

        when(roomRepository.findByHotel(hotel)).thenReturn(List.of(room));
        when(inventoryRepository.findExistingDates(eq(9L), any(), any())).thenReturn(everyDate);

        inventoryService.refreshHotelInventory(hotel);

        verify(hotelMinPriceService, never()).updateHotelMinPrice(anyLong());
    }

    @SuppressWarnings("unchecked")
    private List<Inventory> capturedRows() {
        ArgumentCaptor<List<Inventory>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}
