package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.RoomDto;
import com.sameerahmed.projects.airBnbApp.service.RoomService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/hotels/{hotelId}/rooms")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Rooms")
@SecurityRequirement(name = "bearerAuth")
public class RoomAdminController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomDto> createNewRoom(@PathVariable Long hotelId,
                                                 @Valid @RequestBody RoomDto roomDto) {
        RoomDto room = roomService.createNewRoom(hotelId, roomDto);
        return new ResponseEntity<>(room, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<RoomDto>> getAllRoomsInHotel(@PathVariable Long hotelId) {
        return ResponseEntity.ok(roomService.getAllRoomsInHotel(hotelId));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDto> getRoomById(@PathVariable Long roomId) {
        return ResponseEntity.ok(roomService.getRoomById(roomId));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoomById(@PathVariable Long roomId) {
        roomService.deleteRoomById(roomId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{roomId}")
    public ResponseEntity<RoomDto> updateRoomById(@PathVariable Long hotelId,
                                                  @PathVariable Long roomId,
                                                  @Valid @RequestBody RoomDto roomDto) {
        return ResponseEntity.ok(roomService.updateRoomById(hotelId, roomId, roomDto));
    }
}
