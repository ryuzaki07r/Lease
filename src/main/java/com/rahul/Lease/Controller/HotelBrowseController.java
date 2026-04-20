package com.rahul.Lease.Controller;

import com.rahul.Lease.Dto.HotelInfoDTO;
import com.rahul.Lease.Dto.HotelPriceDTO;
import com.rahul.Lease.Dto.HotelSearchRequest;
import com.rahul.Lease.Services.Interfaces.HotelService;
import com.rahul.Lease.Services.Interfaces.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@RequestMapping("/hotels")
@Tag(name = "Hotel Browse" , description = "Browse and search hotels")
public class HotelBrowseController {
    private final InventoryService inventoryService;
    private final HotelService hotelService;

    @GetMapping("/search")
    @Operation(summary = "Search for Hotels", description = "Shows hotels based on availability, location, price")
    public ResponseEntity<Page<HotelPriceDTO>> searchHotels(@RequestBody HotelSearchRequest hotelSearchRequest){
        Page<HotelPriceDTO> page = inventoryService.searchHotels(hotelSearchRequest);
        return ResponseEntity.ok(page);
    }
    @GetMapping("/{hotelId}/info")
    @Operation(summary = "Hotel Info", description = "Get information about specific hotel")
    public ResponseEntity<HotelInfoDTO> getHotelInfo(@PathVariable long hotelId){
        return ResponseEntity.ok(hotelService.getHotelInfoById(hotelId));
    }
}
