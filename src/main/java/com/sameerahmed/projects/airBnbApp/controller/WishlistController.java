package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.HotelDto;
import com.sameerahmed.projects.airBnbApp.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/wishlists")
@RequiredArgsConstructor
@Tag(name = "Wishlists")
@SecurityRequirement(name = "bearerAuth")
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/hotels/{hotelId}")
    @Operation(summary = "Add a hotel to the current user's wishlist")
    public ResponseEntity<HotelDto> add(@PathVariable Long hotelId) {
        return new ResponseEntity<>(wishlistService.addToWishlist(hotelId), HttpStatus.CREATED);
    }

    @DeleteMapping("/hotels/{hotelId}")
    @Operation(summary = "Remove a hotel from the wishlist")
    public ResponseEntity<Void> remove(@PathVariable Long hotelId) {
        wishlistService.removeFromWishlist(hotelId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List wishlist hotels")
    public ResponseEntity<List<HotelDto>> list() {
        return ResponseEntity.ok(wishlistService.getMyWishlist());
    }
}
