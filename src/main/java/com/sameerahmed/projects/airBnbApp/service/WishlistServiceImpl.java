package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.HotelDto;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.User;
import com.sameerahmed.projects.airBnbApp.entity.WishlistItem;
import com.sameerahmed.projects.airBnbApp.exception.ResourceNotFoundException;
import com.sameerahmed.projects.airBnbApp.repository.HotelRepository;
import com.sameerahmed.projects.airBnbApp.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.sameerahmed.projects.airBnbApp.util.AppUtils.getCurrentUser;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final HotelRepository hotelRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public HotelDto addToWishlist(Long hotelId) {
        User user = getCurrentUser();
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with id: " + hotelId));

        if (!wishlistRepository.existsByUserAndHotel(user, hotel)) {
            WishlistItem item = new WishlistItem();
            item.setUser(user);
            item.setHotel(hotel);
            wishlistRepository.save(item);
        }
        return modelMapper.map(hotel, HotelDto.class);
    }

    @Override
    @Transactional
    public void removeFromWishlist(Long hotelId) {
        User user = getCurrentUser();
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with id: " + hotelId));
        wishlistRepository.deleteByUserAndHotel(user, hotel);
    }

    @Override
    public List<HotelDto> getMyWishlist() {
        return wishlistRepository.findByUserOrderByCreatedAtDesc(getCurrentUser()).stream()
                .map(item -> modelMapper.map(item.getHotel(), HotelDto.class))
                .collect(Collectors.toList());
    }
}
