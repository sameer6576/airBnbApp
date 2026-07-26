package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.BookingDto;
import com.sameerahmed.projects.airBnbApp.dto.UserDto;
import com.sameerahmed.projects.airBnbApp.dto.UserProfileUpdateDto;
import com.sameerahmed.projects.airBnbApp.service.BookingService;
import com.sameerahmed.projects.airBnbApp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final BookingService bookingService;

    @PatchMapping("/profile")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<Void> updateProfile(@RequestBody UserProfileUpdateDto userProfileUpdateDto) {
        userService.updateProfile(userProfileUpdateDto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/myBookings")
    @Operation(summary = "List bookings for the current user")
    public ResponseEntity<List<BookingDto>> getMyBookings() {
        return ResponseEntity.ok(bookingService.getMyBookings());
    }

    @GetMapping("/getMyProfile")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<UserDto> getMyProfile() {
        return ResponseEntity.ok(bookingService.getMyProfile());
    }
}
