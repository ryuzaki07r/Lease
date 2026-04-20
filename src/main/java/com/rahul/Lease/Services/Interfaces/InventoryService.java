package com.rahul.Lease.Services.Interfaces;

import com.rahul.Lease.Dto.HotelPriceDTO;
import com.rahul.Lease.Dto.HotelSearchRequest;
import com.rahul.Lease.Dto.InventoryDTO;
import com.rahul.Lease.Dto.UpdateInventoryRequestDTO;
import com.rahul.Lease.Models.Room;
import org.springframework.data.domain.Page;

import java.util.List;

public interface InventoryService {
    void initializeRoomForAYear(Room room);

    void deleteAllInventories(Room room);

    Page<HotelPriceDTO> searchHotels(HotelSearchRequest hotelSearchRequest);

    List<InventoryDTO> getAllInventoryByRoom(Long roomId);

    void updateInventory(Long roomId, UpdateInventoryRequestDTO updateInventoryRequestDto);

}
