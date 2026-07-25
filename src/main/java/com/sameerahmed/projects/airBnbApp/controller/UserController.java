package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.BookingDto;
import com.sameerahmed.projects.airBnbApp.dto.UserDto;
import com.sameerahmed.projects.airBnbApp.dto.UserProfileUpdateDto;
import com.sameerahmed.projects.airBnbApp.service.BookingService;
import com.sameerahmed.projects.airBnbApp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final BookingService bookingService;

    @PatchMapping("/profile")
    public ResponseEntity<Void> updateProfile(@RequestBody UserProfileUpdateDto userProfileUpdateDto){
        userService.updateProfile(userProfileUpdateDto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/myBookings")
    public ResponseEntity<List<BookingDto>> getMyBookings(){
        return ResponseEntity.ok(bookingService.getMyBookings());
    }

    @GetMapping("/getMyProfile")
    public ResponseEntity<UserDto> getMyProfile(){
        return ResponseEntity.ok(bookingService.getMyProfile());
    }
}
