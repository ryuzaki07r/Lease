package com.rahul.Lease.Services;

import com.rahul.Lease.Dto.RoomDTO;
import com.rahul.Lease.Exceptions.ResourceNotFoundException;
import com.rahul.Lease.Exceptions.UnauthorizedException;
import com.rahul.Lease.Models.Hotel;
import com.rahul.Lease.Models.Room;
import com.rahul.Lease.Models.User;
import com.rahul.Lease.Repository.BookingRepository;
import com.rahul.Lease.Repository.HotelRepository;
import com.rahul.Lease.Repository.InventoryRepository;
import com.rahul.Lease.Repository.RoomRepository;
import com.rahul.Lease.Services.Interfaces.InventoryService;
import com.rahul.Lease.Services.Interfaces.RoomService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.rahul.Lease.Utils.AppUtils.getCurrentUser;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {
    private final ModelMapper modelMapper;
    private final RoomRepository roomRepository;
    private final InventoryService inventoryService;
    private final HotelRepository hotelRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public RoomDTO createNewRoom(Long hotelId, RoomDTO roomDto) {
        log.info("Creating room for hotel with id: {}", hotelId);
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(
                ()-> new ResourceNotFoundException("No hotel found with id: " +hotelId));
        User user = getCurrentUser();
        if(!user.equals(hotel.getOwner())){
            throw new UnauthorizedException("You are not the owner of this hotel with id: " +hotelId);
        }
        Room room = modelMapper.map(roomDto, Room.class);
        room.setId(null);
        room.setHotel(hotel);
        room = roomRepository.save(room);
        if (hotel.isActive()){
            inventoryService.initializeRoomForAYear(room);
        }
        return modelMapper.map(room, RoomDTO.class);
    }

    @Override
    public List<RoomDTO> getAllRoomsInHotel(Long hotelId) {
        log.info("Getting all rooms in hotel with ID: {}", hotelId);
        Hotel hotel = hotelRepository
                .findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: "+hotelId));
        User user = getCurrentUser();
        if(!user.equals(hotel.getOwner())){
            throw new UnauthorizedException("You are not the owner of the hotel with id: " +hotelId);
        }
        return hotel.getRooms().stream()
                .map(room -> modelMapper.map(room, RoomDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public RoomDTO getRoomById(Long hotelId, Long roomId) {
        log.info("Getting the room with ID: {}", roomId);
        Room room = roomRepository
                .findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: "+roomId));
        if(!room.getHotel().getId().equals(hotelId)){
            throw new ResourceNotFoundException("Room " + roomId + " not found in hotel " + hotelId);
        }
        User user = getCurrentUser();
        if(!user.equals(room.getHotel().getOwner())){
            throw new UnauthorizedException("You are not the owner of this hotel with id: " +hotelId);
        }
        return modelMapper.map(room, RoomDTO.class);
    }

    @Override
    @Transactional
    public void deleteRoomById(Long roomId) {
        log.info("Deleting room with id: {}", roomId);
        Room room = roomRepository.findById(roomId).orElseThrow(
                ()-> new ResourceNotFoundException("There is no room found with id: " +roomId));
        User user = getCurrentUser();
        if(!user.equals(room.getHotel().getOwner())){
            throw new UnauthorizedException("You are not the owner of this hotel with id: "+room.getHotel().getId());
        }
        if(bookingRepository.existsByRoom(room)){
            throw new IllegalStateException("Room " + roomId + " has bookings and can not be deleted");
        }
        inventoryService.deleteAllInventories(room);
        roomRepository.deleteById(roomId);
    }

    @Override
    @Transactional
    public RoomDTO updateRoomById(Long hotelId, Long roomId, RoomDTO roomDto) {
        log.info("updating hotel with id: {} and room with id: {}", hotelId,roomId);
        Hotel hotel = hotelRepository
                .findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: "+hotelId));

        User user = getCurrentUser();
        if(!user.equals(hotel.getOwner())){
            throw new UnauthorizedException("You are not the owner of this hotel with id: " +hotelId);
        }
        Room room = roomRepository.findById(roomId).orElseThrow(
                ()-> new ResourceNotFoundException("No room found with id: " +roomId));
        if(!room.getHotel().getId().equals(hotelId)){
            throw new ResourceNotFoundException("Room " + roomId + " not found in hotel " + hotelId);
        }
        modelMapper.map(roomDto,room);
        room.setId(roomId);
        room = roomRepository.save(room);

        return modelMapper.map(room, RoomDTO.class);

    }
}
