package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.HotelMinPrice;
import com.sameerahmed.projects.airBnbApp.exception.ResourceNotFoundException;
import com.sameerahmed.projects.airBnbApp.repository.HotelMinPriceRepository;
import com.sameerahmed.projects.airBnbApp.repository.HotelRepository;
import com.sameerahmed.projects.airBnbApp.repository.InventoryRepository;
import com.sameerahmed.projects.airBnbApp.repository.projection.DailyMinPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HotelMinPriceServiceImpl implements HotelMinPriceService {

    private final HotelRepository hotelRepository;
    private final InventoryRepository inventoryRepository;
    private final HotelMinPriceRepository hotelMinPriceRepository;

    @Override
    @Transactional
    public void updateHotelMinPrice(Long hotelId) {

        log.debug("Updating hotel minimum prices for hotel {}", hotelId);

        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Hotel not found with id: " + hotelId));

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusYears(1);

        // Fetch minimum room price for each day directly from the database
        List<DailyMinPrice> dailyMinPrices =
                inventoryRepository.findDailyMinimumPrices(
                        hotel,
                        startDate,
                        endDate
                );

        // Remove existing minimum prices for the hotel
        hotelMinPriceRepository.deleteByHotel(hotel);

        // Prepare new HotelMinPrice entities
        List<HotelMinPrice> hotelMinPrices = new ArrayList<>();

        for (DailyMinPrice dailyPrice : dailyMinPrices) {

            HotelMinPrice hotelMinPrice =
                    new HotelMinPrice(hotel, dailyPrice.getDate());

            hotelMinPrice.setPrice(dailyPrice.getPrice());

            hotelMinPrices.add(hotelMinPrice);
        }

        hotelMinPriceRepository.saveAll(hotelMinPrices);

        log.debug("Saved {} hotel minimum price records for hotel {}",
                hotelMinPrices.size(), hotelId);
    }
}