package com.rahul.Lease.Services.Interfaces;

import com.rahul.Lease.Dto.HotelDTO;
import com.rahul.Lease.Dto.HotelInfoDTO;
import com.rahul.Lease.Models.HotelAddress;

import java.util.List;

public interface HotelService {
    HotelDTO createNewHotel(HotelDTO hotelDto);
    HotelDTO getHotelById(Long id);
    HotelDTO updateHotelById(Long id, HotelDTO hotelDto);
    void deleteHotelById(Long id);
    void activateHotel(Long hotelId);
    HotelInfoDTO getHotelInfoById(Long hotelId);
    List<HotelDTO> getAllHotels();
}
