package com.rahul.Lease.Services;

import com.rahul.Lease.Dto.HotelDTO;
import com.rahul.Lease.Dto.HotelPriceDTO;
import com.rahul.Lease.Dto.HotelPriceResponseDTO;
import com.rahul.Lease.Dto.HotelSearchRequest;
import com.rahul.Lease.Dto.InventoryDTO;
import com.rahul.Lease.Dto.UpdateInventoryRequestDTO;
import com.rahul.Lease.Exceptions.ResourceNotFoundException;
import com.rahul.Lease.Exceptions.UnauthorizedException;
import com.rahul.Lease.Models.Inventory;
import com.rahul.Lease.Models.Room;
import com.rahul.Lease.Models.User;
import com.rahul.Lease.Repository.HotelMinPriceRepository;
import com.rahul.Lease.Repository.InventoryRepository;
import com.rahul.Lease.Repository.RoomRepository;
import com.rahul.Lease.Services.Interfaces.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.rahul.Lease.Utils.AppUtils.getCurrentUser;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    private final InventoryRepository inventoryRepository;
    private final ModelMapper modelMapper;
    private final RoomRepository roomRepository;
    private final HotelMinPriceRepository hotelMinPriceRepository;

    @Override
    public void initializeRoomForAYear(Room room) {
        List<Inventory> inventories = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusYears(1);

        for(LocalDate date = today;!date.isAfter(endDate);date = date.plusDays(1)){
            Inventory inventory = Inventory.builder()
                    .hotel(room.getHotel())
                    .room(room)
                    .bookCount(0)
                    .reservedCount(0)
                    .city(room.getHotel().getCity())
                    .date(date)
                    .price(room.getBasePrice())
                    .surgeFactor(BigDecimal.ONE)
                    .totalCount(room.getTotalCount())
                    .closed(false)
                    .build();
            inventories.add(inventory);
        }
        inventoryRepository.saveAll(inventories);
    }

    @Override
    public void deleteAllInventories(Room room) {
        log.info("Deleting inventories of Room with Id: {} ", room.getId());
        inventoryRepository.deleteByRoom(room);

    }

    @Override
    public Page<HotelPriceResponseDTO> searchHotels(HotelSearchRequest hotelSearchRequest) {
        log.info("Searching hotels in {} from {} to {}", hotelSearchRequest.getCity(),
                hotelSearchRequest.getStartDate(), hotelSearchRequest.getEndDate());

        // endDate is the checkout date, so it is not counted as a night
        long dateCount = ChronoUnit.DAYS.between(hotelSearchRequest.getStartDate(),
                hotelSearchRequest.getEndDate());
        if(dateCount < 1){
            throw new IllegalArgumentException("endDate must be after startDate");
        }
        Pageable pageable = PageRequest.of(hotelSearchRequest.getPage(), hotelSearchRequest.getSize());
        Page<HotelPriceDTO> hotelPage = hotelMinPriceRepository.findHotelsWithAvailableInventory(
                hotelSearchRequest.getCity(), hotelSearchRequest.getStartDate(), hotelSearchRequest.getEndDate()
                ,hotelSearchRequest.getRoomsCount(), dateCount, pageable);
        return hotelPage.map(element-> new HotelPriceResponseDTO(
                modelMapper.map(element.getHotel(), HotelDTO.class), element.getPrice()));
    }

    @Override
    public List<InventoryDTO> getAllInventoryByRoom(Long roomId) {
        log.info("Getting all inventory by room with room id: {}", roomId);
        Room room = roomRepository.findById(roomId).orElseThrow(
                ()-> new ResourceNotFoundException("There is no room found with roomId: " +roomId));
        User user = getCurrentUser();
        if(!user.equals(room.getHotel().getOwner())){
            throw new UnauthorizedException("you are not the owner of room with id: " +roomId);
        }
        return inventoryRepository.findByRoomOrderByDate(room).stream()
                .map((element)-> modelMapper.map(element, InventoryDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateInventory(Long roomId, UpdateInventoryRequestDTO updateInventoryRequestDto) {
        log.info("updating inventory with room id: {} and date between {} to {}", roomId
        ,updateInventoryRequestDto.getStartDate(), updateInventoryRequestDto.getEndDate());

        Room room = roomRepository.findById(roomId).orElseThrow(
                ()-> new ResourceNotFoundException("Room can not be found with id: " +roomId));
        User user = getCurrentUser();
        if(!user.equals(room.getHotel().getOwner())){
            throw new UnauthorizedException("you are not the owner of the room with id: " +roomId);
        }
        inventoryRepository.getInventoryAndLockBeforeUpdate(roomId,
                updateInventoryRequestDto.getStartDate(), updateInventoryRequestDto.getEndDate());

        inventoryRepository.updateInventory(roomId, updateInventoryRequestDto.getStartDate(),
                updateInventoryRequestDto.getEndDate(), updateInventoryRequestDto.getClosed(),
                updateInventoryRequestDto.getSurgeFactor());
    }
}
