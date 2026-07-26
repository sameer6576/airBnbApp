package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.HotelDto;
import com.sameerahmed.projects.airBnbApp.service.WishlistService;
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
    public ResponseEntity<HotelDto> add(@PathVariable Long hotelId) {
        return new ResponseEntity<>(wishlistService.addToWishlist(hotelId), HttpStatus.CREATED);
    }

    @DeleteMapping("/hotels/{hotelId}")
    public ResponseEntity<Void> remove(@PathVariable Long hotelId) {
        wishlistService.removeFromWishlist(hotelId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<HotelDto>> list() {
        return ResponseEntity.ok(wishlistService.getMyWishlist());
    }
}
