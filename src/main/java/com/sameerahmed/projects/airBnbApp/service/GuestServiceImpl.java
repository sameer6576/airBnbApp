package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.GuestDto;
import com.sameerahmed.projects.airBnbApp.entity.Guest;
import com.sameerahmed.projects.airBnbApp.entity.User;
import com.sameerahmed.projects.airBnbApp.exception.ResourceNotFoundException;
import com.sameerahmed.projects.airBnbApp.repository.GuestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.sameerahmed.projects.airBnbApp.util.AppUtils.getCurrentUser;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuestServiceImpl implements GuestService {

    private final GuestRepository guestRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public GuestDto createGuest(GuestDto guestDto) {
        User user = getCurrentUser();
        if (guestDto.getName() == null || guestDto.getName().isBlank()) {
            throw new IllegalArgumentException("Guest name is required");
        }
        Guest guest = modelMapper.map(guestDto, Guest.class);
        guest.setId(null);
        guest.setUser(user);
        guest = guestRepository.save(guest);
        return toDto(guest);
    }

    @Override
    public List<GuestDto> getMyGuests() {
        User user = getCurrentUser();
        return guestRepository.findByUser(user).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GuestDto updateGuest(Long guestId, GuestDto guestDto) {
        Guest guest = getOwnedGuest(guestId);
        guest.setName(guestDto.getName());
        guest.setGender(guestDto.getGender());
        guest.setAge(guestDto.getAge());
        return toDto(guestRepository.save(guest));
    }

    @Override
    @Transactional
    public void deleteGuest(Long guestId) {
        Guest guest = getOwnedGuest(guestId);
        guestRepository.delete(guest);
    }

    private Guest getOwnedGuest(Long guestId) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found with id: " + guestId));
        User user = getCurrentUser();
        if (!Objects.equals(user.getId(), guest.getUser().getId())) {
            throw new AccessDeniedException("Guest does not belong to this user with id: " + user.getId());
        }
        return guest;
    }

    private GuestDto toDto(Guest guest) {
        return modelMapper.map(guest, GuestDto.class);
    }
}
