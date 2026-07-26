package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.HotelInfoDto;
import com.sameerahmed.projects.airBnbApp.dto.HotelPriceDto;
import com.sameerahmed.projects.airBnbApp.dto.HotelSearchRequest;
import com.sameerahmed.projects.airBnbApp.service.HotelService;
import com.sameerahmed.projects.airBnbApp.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hotels")
@RequiredArgsConstructor
@Tag(name = "Hotel Browse")
public class HotelBrowseController {

    private final InventoryService inventoryService;
    private final HotelService hotelService;

    @PostMapping("/search")
    @Operation(summary = "Search hotels with available inventory for the date range")
    public ResponseEntity<Page<HotelPriceDto>> searchHotels(@Valid @RequestBody HotelSearchRequest hotelSearchRequest) {
        Page<HotelPriceDto> page = inventoryService.searchHotels(hotelSearchRequest);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{hotelId}/info")
    @Operation(summary = "Get public hotel details and rooms")
    public ResponseEntity<HotelInfoDto> getHotelInfo(@PathVariable Long hotelId) {
        return ResponseEntity.ok(hotelService.getHotelInfoById(hotelId));
    }
}
