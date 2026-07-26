package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.GuestDto;
import com.sameerahmed.projects.airBnbApp.service.GuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/guests")
@RequiredArgsConstructor
@Tag(name = "Guests")
@SecurityRequirement(name = "bearerAuth")
public class GuestController {

    private final GuestService guestService;

    @PostMapping
    @Operation(summary = "Create a guest for the current user")
    public ResponseEntity<GuestDto> createGuest(@RequestBody GuestDto guestDto) {
        return new ResponseEntity<>(guestService.createGuest(guestDto), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List guests for the current user")
    public ResponseEntity<List<GuestDto>> getMyGuests() {
        return ResponseEntity.ok(guestService.getMyGuests());
    }

    @PutMapping("/{guestId}")
    @Operation(summary = "Update a guest owned by the current user")
    public ResponseEntity<GuestDto> updateGuest(@PathVariable Long guestId, @RequestBody GuestDto guestDto) {
        return ResponseEntity.ok(guestService.updateGuest(guestId, guestDto));
    }

    @DeleteMapping("/{guestId}")
    @Operation(summary = "Delete a guest owned by the current user")
    public ResponseEntity<Void> deleteGuest(@PathVariable Long guestId) {
        guestService.deleteGuest(guestId);
        return ResponseEntity.noContent().build();
    }
}
