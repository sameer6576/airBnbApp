package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.HotelDto;
import com.sameerahmed.projects.airBnbApp.dto.HotelInfoDto;
import com.sameerahmed.projects.airBnbApp.dto.HotelReportDto;

import java.time.LocalDate;
import java.util.List;

public interface HotelService {
    HotelDto createNewHotel(HotelDto hotelDto);

    HotelDto getHotelById(Long id);

    HotelDto updateHotelById(Long id, HotelDto hotelDto);

    void deleteHotelById(Long id);

    void activateHotel(Long id);

    HotelInfoDto getHotelInfoById(Long hotelId);

    List<HotelDto> getAllHotels();
}
