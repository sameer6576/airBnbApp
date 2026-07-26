package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.GuestDto;

import java.util.List;

public interface GuestService {
    GuestDto createGuest(GuestDto guestDto);

    List<GuestDto> getMyGuests();

    GuestDto updateGuest(Long guestId, GuestDto guestDto);

    void deleteGuest(Long guestId);
}
