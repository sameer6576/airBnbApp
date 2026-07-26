package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.UserDto;
import com.sameerahmed.projects.airBnbApp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Users")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final UserService userService;

    @PostMapping("/{userId}/promote-manager")
    @Operation(summary = "Promote a user to HOTEL_MANAGER (ADMIN only)")
    public ResponseEntity<UserDto> promoteToManager(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.promoteToHotelManager(userId));
    }
}
