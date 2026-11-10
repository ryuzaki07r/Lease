package com.rahul.Lease.Services;

import com.rahul.Lease.Dto.HotelDTO;
import com.rahul.Lease.Dto.HotelInfoDTO;
import com.rahul.Lease.Dto.RoomDTO;
import com.rahul.Lease.Exceptions.ResourceNotFoundException;
import com.rahul.Lease.Exceptions.UnauthorizedException;
import com.rahul.Lease.Models.Hotel;
import com.rahul.Lease.Models.HotelAddress;
import com.rahul.Lease.Models.Room;
import com.rahul.Lease.Models.User;
import com.rahul.Lease.Repository.HotelRepository;
import com.rahul.Lease.Repository.RoomRepository;
import com.rahul.Lease.Services.Interfaces.HotelService;
import com.rahul.Lease.Services.Interfaces.InventoryService;
import com.rahul.Lease.Utils.AppUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.rahul.Lease.Utils.AppUtils.getCurrentUser;

@Service
@AllArgsConstructor
@Slf4j
public class HotelServiceImpl implements HotelService {
    private final HotelRepository hotelRepository;
    private final ModelMapper modelMapper;
    private final InventoryService inventoryService;
    private final RoomRepository roomRepository;
    private final PricingUpdateService pricingUpdateService;
    @Override
    public HotelDTO createNewHotel(HotelDTO hotelDto) {
        log.info("Creating a new hotel with name: {}", hotelDto.getName());
        Hotel hotel = modelMapper.map(hotelDto,Hotel.class);
        hotel.setId(null);
        hotel.setActive(false);

        User user = getCurrentUser();
        hotel.setOwner(user);
        hotel = hotelRepository.save(hotel);
        log.info("Hotel created successfully with name: {}", hotel.getName());
        return modelMapper.map(hotel,HotelDTO.class);
    }

    @Override
    public HotelDTO getHotelById(Long id) {
        log.info("Getting the hotel with id: {}", id);

        User user = getCurrentUser();
        Hotel hotel = hotelRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Hotel not found with id: " +id));

        if(!user.equals(hotel.getOwner())){
            throw new UnauthorizedException("you are not the owner of the hotel with id: " +id);
        }
        return modelMapper.map(hotel, HotelDTO.class);
    }

    @Override
    public HotelDTO updateHotelById(Long id, HotelDTO hotelDto) {
        log.info("updating hotel with hotel ID: ",id);
        Hotel hotel  = hotelRepository.findById(id).orElseThrow(
                ()-> new ResourceNotFoundException("Hotel can not be found with id:" +id));
        User user=getCurrentUser();
        if(!user.equals(hotel.getOwner())){
            throw new UnauthorizedException("you are not the owner of the hotel eith id: " +id);
        }
        modelMapper.map(hotelDto, hotel);
        hotel.setId(id);
        hotel = hotelRepository.save(hotel);
        return modelMapper.map(hotel, HotelDTO.class);
    }

    @Override
    @Transactional
    public void deleteHotelById(Long id) {
        Hotel hotel  = hotelRepository.findById(id).orElseThrow(
                ()-> new ResourceNotFoundException("Hotel can not be found with id:" +id));
        User user=getCurrentUser();
        if(!user.equals(hotel.getOwner())){
            throw new UnauthorizedException("you are not the owner of the hotel eith id: " +id);
        }
        for (Room room : hotel.getRooms()){
            inventoryService.deleteAllInventories(room);
            roomRepository.deleteById(room.getId());
        }
        hotelRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void activateHotel(Long hotelId) {
        log.info("Activating hotel with id: {}", hotelId);
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(
                ()-> new ResourceNotFoundException("No hotel with hotel id: " +hotelId));

        User user = getCurrentUser();
        if(!user.equals(hotel.getOwner())){
            throw new UnauthorizedException("you are not the owner of this hotel with id: " +hotelId);
        }
        hotel.setActive(true);

        for(Room room: hotel.getRooms()) {
            inventoryService.initializeRoomForAYear(room);
        }
        // Populate HotelMinPrice now so the hotel shows up in search without waiting for the hourly job
        pricingUpdateService.updateHotelPrices(hotel);
    }

    @Override
    public HotelInfoDTO getHotelInfoById(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(
                () -> new ResourceNotFoundException("Hotel not found with ID: "+hotelId));

        List<RoomDTO> rooms = hotel.getRooms().stream()
                .map((elements) -> modelMapper.map(elements, RoomDTO.class))
                .toList();

        return new HotelInfoDTO(modelMapper.map(hotel,HotelDTO.class),rooms);
    }

    @Override
    public List<HotelDTO> getAllHotels() {
        User user = getCurrentUser();
        log.info("Getting hotels for user with id: {}", user.getId());
        List<Hotel> hotels = hotelRepository.findByOwner(user);
        return hotels.stream()
                .map(hotel->modelMapper.map(hotel,HotelDTO.class))
                .toList();
    }
}
