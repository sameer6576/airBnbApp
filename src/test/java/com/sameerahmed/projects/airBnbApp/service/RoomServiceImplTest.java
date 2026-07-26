package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.RoomDto;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.Room;
import com.sameerahmed.projects.airBnbApp.entity.User;
import com.sameerahmed.projects.airBnbApp.repository.HotelRepository;
import com.sameerahmed.projects.airBnbApp.repository.RoomRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    @Mock private RoomRepository roomRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private InventoryService inventoryService;
    @Mock private ModelMapper modelMapper;

    @InjectMocks
    private RoomServiceImpl roomService;

    private User owner;
    private Hotel hotel;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setEmail("manager@example.com");
        owner.setRoles(java.util.Set.of(com.sameerahmed.projects.airBnbApp.entity.enums.Role.HOTEL_MANAGER));

        hotel = new Hotel();
        hotel.setId(3L);
        hotel.setOwner(owner);
        hotel.setActive(false);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities())
        );
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRoomOnActiveHotelInitializesInventory() {
        hotel.setActive(true);
        RoomDto request = new RoomDto();
        request.setType("Suite");
        request.setBasePrice(BigDecimal.valueOf(200));
        request.setTotalCount(2);
        request.setCapacity(2);

        Room mapped = new Room();
        mapped.setType("Suite");
        mapped.setBasePrice(BigDecimal.valueOf(200));
        mapped.setTotalCount(2);
        mapped.setCapacity(2);

        when(hotelRepository.findById(3L)).thenReturn(Optional.of(hotel));
        when(modelMapper.map(request, Room.class)).thenReturn(mapped);
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> {
            Room saved = inv.getArgument(0);
            saved.setId(9L);
            return saved;
        });
        when(modelMapper.map(any(Room.class), org.mockito.ArgumentMatchers.eq(RoomDto.class)))
                .thenReturn(request);

        roomService.createNewRoom(3L, request);

        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(inventoryService).initializeRoomForAYear(roomCaptor.capture());
        assertEquals(9L, roomCaptor.getValue().getId());
    }

    @Test
    void createRoomOnInactiveHotelSkipsInventory() {
        hotel.setActive(false);
        RoomDto request = new RoomDto();
        request.setType("Twin");
        request.setBasePrice(BigDecimal.valueOf(100));
        request.setTotalCount(3);
        request.setCapacity(2);

        Room mapped = new Room();
        when(hotelRepository.findById(3L)).thenReturn(Optional.of(hotel));
        when(modelMapper.map(request, Room.class)).thenReturn(mapped);
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
        when(modelMapper.map(any(Room.class), org.mockito.ArgumentMatchers.eq(RoomDto.class)))
                .thenReturn(request);

        roomService.createNewRoom(3L, request);

        verify(inventoryService, never()).initializeRoomForAYear(any());
    }
}
