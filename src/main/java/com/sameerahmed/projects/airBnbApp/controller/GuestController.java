package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.GuestDto;
import com.sameerahmed.projects.airBnbApp.service.GuestService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    public ResponseEntity<GuestDto> createGuest(@Valid @RequestBody GuestDto guestDto) {
        return new ResponseEntity<>(guestService.createGuest(guestDto), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<GuestDto>> getMyGuests() {
        return ResponseEntity.ok(guestService.getMyGuests());
    }

    @PutMapping("/{guestId}")
    public ResponseEntity<GuestDto> updateGuest(@PathVariable Long guestId, @Valid @RequestBody GuestDto guestDto) {
        return ResponseEntity.ok(guestService.updateGuest(guestId, guestDto));
    }

    @DeleteMapping("/{guestId}")
    public ResponseEntity<Void> deleteGuest(@PathVariable Long guestId) {
        guestService.deleteGuest(guestId);
        return ResponseEntity.noContent().build();
    }
}
