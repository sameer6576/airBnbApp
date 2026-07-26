package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.dto.InventoryDto;
import com.sameerahmed.projects.airBnbApp.dto.UpdateInventoryRequestDto;
import com.sameerahmed.projects.airBnbApp.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
@Tag(name = "Admin Inventory")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "List inventory calendar for a room")
    public ResponseEntity<List<InventoryDto>> getAllInventoryByRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(inventoryService.getAllInventoryByRoom(roomId));
    }

    @PatchMapping("/rooms/{roomId}")
    @Operation(summary = "Update surge/closed flags for a date range")
    public ResponseEntity<Void> updateInventory(@PathVariable Long roomId,
                                                @Valid @RequestBody UpdateInventoryRequestDto updateInventoryRequestDto) {
        inventoryService.updateInventory(roomId, updateInventoryRequestDto);
        return ResponseEntity.noContent().build();
    }
}
