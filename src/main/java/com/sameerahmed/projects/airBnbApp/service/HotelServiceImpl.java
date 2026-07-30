package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.HotelDto;
import com.sameerahmed.projects.airBnbApp.dto.HotelInfoDto;
import com.sameerahmed.projects.airBnbApp.dto.RoomDto;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.Room;
import com.sameerahmed.projects.airBnbApp.entity.User;
import com.sameerahmed.projects.airBnbApp.entity.enums.BookingStatus;
import com.sameerahmed.projects.airBnbApp.exception.ResourceNotFoundException;
import com.sameerahmed.projects.airBnbApp.repository.BookingRepository;
import com.sameerahmed.projects.airBnbApp.repository.HotelMinPriceRepository;
import com.sameerahmed.projects.airBnbApp.repository.HotelRepository;
import com.sameerahmed.projects.airBnbApp.repository.InventoryRepository;
import com.sameerahmed.projects.airBnbApp.repository.ReviewRepository;
import com.sameerahmed.projects.airBnbApp.repository.RoomRepository;
import com.sameerahmed.projects.airBnbApp.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.sameerahmed.projects.airBnbApp.util.AppUtils.getCurrentUser;

@Service
@Slf4j
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    /** Statuses that represent an outstanding obligation to a guest. */
    private static final List<BookingStatus> LIVE_BOOKING_STATUSES = List.of(
            BookingStatus.RESERVED,
            BookingStatus.GUEST_ADDED,
            BookingStatus.PAYMENT_PENDING,
            BookingStatus.CONFIRMED
    );

    private final HotelRepository hotelRepository;
    private final InventoryService inventoryService;
    private final ModelMapper modelMapper;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final WishlistRepository wishlistRepository;
    private final HotelMinPriceRepository hotelMinPriceRepository;
    private final InventoryRepository inventoryRepository;

    @Override
    public HotelDto createNewHotel(HotelDto hotelDto) {
        log.info("Creating a new hotel with name: {}", hotelDto.getName());
        Hotel hotel = modelMapper.map(hotelDto, Hotel.class);
        hotel.setActive(false);
        hotel.setAverageRating(0.0);
        hotel.setReviewCount(0);

        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        hotel.setOwner(user);
        Hotel savedHotel = hotelRepository.save(hotel);
        log.info("Created a new hotel with ID: {}", hotelDto.getId());
        return modelMapper.map(savedHotel, HotelDto.class);
    }

    @Override
    public HotelDto getHotelById(Long id) {
        log.info("Getting the hotel with ID: {}", id);
        Hotel hotel = hotelRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with id: " + id));
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!Objects.equals(user.getId(), hotel.getOwner().getId())) {
            throw new AccessDeniedException("This user does not own this hotel with id: " + id);
        }
        return modelMapper.map(hotel, HotelDto.class);
    }

    @Override
    @Transactional
    public HotelDto updateHotelById(Long id, HotelDto hotelDto) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Hotel not found with id: " + id));

        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!Objects.equals(user.getId(), hotel.getOwner().getId())) {
            throw new AccessDeniedException("This user does not own this hotel with id: " + id);
        }
        String previousCity = hotel.getCity();

        modelMapper.map(hotelDto, hotel);
        hotel.setAmenities(hotelDto.getAmenities());
        hotel.setPhotos(hotelDto.getPhotos());
        hotel.setId(id);
        hotel = hotelRepository.save(hotel);

        // Inventory keeps a denormalised copy of the city for search. Left stale, the
        // hotel disappears from results for its new city and still matches the old.
        if (!Objects.equals(previousCity, hotel.getCity())) {
            int rows = inventoryRepository.updateCityForHotel(hotel.getId(), hotel.getCity());
            log.info("Hotel {} moved from {} to {}; updated {} inventory rows",
                    id, previousCity, hotel.getCity(), rows);
        }

        return modelMapper.map(hotel, HotelDto.class);
    }

    @Override
    @Transactional
    public void deleteHotelById(Long id) {
        Hotel hotel = hotelRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with id: " + id));
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!Objects.equals(user.getId(), hotel.getOwner().getId())) {
            throw new AccessDeniedException("This user does not own this hotel with id: " + id);
        }

        // A live hold or a confirmed stay is somebody's reservation and, in the
        // confirmed case, a payment record. Refusing is the only safe answer:
        // cascading the delete would erase a guest's booking without telling them.
        if (bookingRepository.existsByHotelAndBookingStatusIn(hotel, LIVE_BOOKING_STATUSES)) {
            throw new IllegalStateException(
                    "Hotel " + id + " has active or confirmed bookings and cannot be deleted");
        }

        // Order matters: every one of these has a non-null foreign key to the rows
        // deleted after it. The previous implementation removed only inventory and
        // rooms, so deleting a hotel that had ever been booked failed on a raw
        // constraint violation surfaced as a 500.
        reviewRepository.deleteByHotel(hotel);
        wishlistRepository.deleteByHotel(hotel);
        bookingRepository.deleteByHotel(hotel);
        hotelMinPriceRepository.deleteByHotel(hotel);

        for (Room room : hotel.getRooms()) {
            inventoryService.deleteAllInventories(room);
            roomRepository.deleteById(room.getId());
        }
        hotelRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void activateHotel(Long id) {
        log.info("Activating the hotel with ID: {}", id);
        Hotel hotel = hotelRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with id: " + id));
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!Objects.equals(user.getId(), hotel.getOwner().getId())) {
            throw new AccessDeniedException("This user does not own this hotel with id: " + id);
        }
        // Idempotent by construction: activating twice tops up whatever is missing
        // rather than regenerating a fixed year and colliding with the inventory
        // unique constraint, which used to surface as a 500.
        hotel.setActive(true);
        hotelRepository.save(hotel);
        inventoryService.refreshHotelInventory(hotel);
    }

    @Override
    public HotelInfoDto getHotelInfoById(Long hotelId) {
        Hotel hotel = hotelRepository
                .findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with id: " + hotelId));

        // This endpoint is public. Unpublished hotels are drafts, and the payload
        // includes contactInfo, so an inactive hotel must look absent rather than
        // merely be absent from search.
        if (!Boolean.TRUE.equals(hotel.getActive())) {
            throw new ResourceNotFoundException("Hotel not found with id: " + hotelId);
        }

        List<RoomDto> rooms = hotel.getRooms()
                .stream()
                .map((element) -> modelMapper.map(element, RoomDto.class))
                .toList();

        return new HotelInfoDto(modelMapper.map(hotel, HotelDto.class), rooms);
    }

    @Override
    public List<HotelDto> getAllHotels() {
        User user = getCurrentUser();
        log.info("Getting all hotels for the admin user with ID: {}", user.getId());

        List<Hotel> hotels = hotelRepository.findByOwner(user);
        return hotels.stream()
                .map((element) -> modelMapper.map(element, HotelDto.class))
                .collect(Collectors.toList());
    }

}
