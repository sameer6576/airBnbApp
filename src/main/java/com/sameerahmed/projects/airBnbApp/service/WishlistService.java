package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.HotelDto;

import java.util.List;

public interface WishlistService {
    HotelDto addToWishlist(Long hotelId);

    void removeFromWishlist(Long hotelId);

    List<HotelDto> getMyWishlist();
}
