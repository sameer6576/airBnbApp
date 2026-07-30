package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.*;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.Room;
import org.springframework.data.domain.Page;

import java.util.List;

public interface InventoryService {

    /**
     * Creates any inventory rows a room is missing between today and the
     * configured horizon, and returns how many it created. Idempotent: calling it
     * repeatedly is a no-op once the horizon is full, which is what lets the same
     * method serve first-time activation and the daily roll-forward.
     */
    int ensureInventoryHorizon(Room room);

    /**
     * Tops up every room of a hotel and refreshes the search min-price table once,
     * rather than once per room.
     */
    void refreshHotelInventory(Hotel hotel);

    void deleteAllInventories(Room room);

    Page<HotelPriceDto> searchHotels(HotelSearchRequest hotelSearchRequest);

    List<InventoryDto> getAllInventoryByRoom(Long roomId);

    void updateInventory(Long roomId, UpdateInventoryRequestDto updateInventoryRequestDto);

    void syncFutureInventoryForRoom(Room room);

    void bulkUpdateInventoryForHotel(Long hotelId, BulkInventoryUpdateRequest request);
}
