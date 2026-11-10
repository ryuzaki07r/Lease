package com.rahul.Lease.Services.Interfaces;

import com.rahul.Lease.Dto.RoomDTO;

import java.util.List;

public interface RoomService {
    RoomDTO createNewRoom(Long hotelId, RoomDTO roomDto);

    List<RoomDTO> getAllRoomsInHotel(Long hotelId);

    RoomDTO getRoomById(Long hotelId, Long roomId);

    void deleteRoomById(Long roomId);

    RoomDTO updateRoomById(Long hotelId, Long roomId, RoomDTO roomDto);
}
