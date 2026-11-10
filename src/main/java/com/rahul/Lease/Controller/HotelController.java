package com.rahul.Lease.Controller;

import com.rahul.Lease.Dto.BookingDTO;
import com.rahul.Lease.Dto.HotelDTO;
import com.rahul.Lease.Dto.HotelReportDTO;
import com.rahul.Lease.Services.Interfaces.BookingService;
import com.rahul.Lease.Services.Interfaces.HotelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/hotels")
@Slf4j
@Tag(name = "Hotel Management", description = "Manage Hotel")
@SecurityRequirement(name = "BearerAuth")
public class HotelController {
    private final HotelService hotelService;
    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Create a new hotel", description = "Adding a new Hotel")
    public ResponseEntity<HotelDTO> createNewHotel(@RequestBody @Valid HotelDTO hotelDTO){
        log.info("Creating a new hotel with name: {}", hotelDTO.getName());
        HotelDTO hotel = hotelService.createNewHotel(hotelDTO);
        return new ResponseEntity<>(hotel, HttpStatus.CREATED);
    }
    @GetMapping("/{hotelId}/")
    @Operation(summary = "Get hotel by Id", description = "Fetching hotel details with Id")
    public ResponseEntity<HotelDTO> getHotelById(@PathVariable long hotelId){
        HotelDTO hotelDTO = hotelService.getHotelById(hotelId);
        return ResponseEntity.ok(hotelDTO);
    }
    @PutMapping("/{hotelId}")
    @Operation(summary = "Updating Hotel", description = "Modifying hotel details based on Id")
    public ResponseEntity<HotelDTO> updateHotelById(@PathVariable long hotelId, @RequestBody @Valid HotelDTO hotelDTO){
        HotelDTO hotel = hotelService.updateHotelById(hotelId, hotelDTO);
        return ResponseEntity.ok(hotel);
    }
    @DeleteMapping("/{hotelId}")
    @Operation(summary = "Delete a hotel", description = "Deleting a hotel from the system")
    public ResponseEntity<Void> deleteHotelById(@PathVariable long hotelId){
        hotelService.deleteHotelById(hotelId);
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{hotelId}/activate")
    @Operation(summary = "Activating Hotel", description = "Marks a Hotel active")
    public ResponseEntity<Void> activateHotel(@PathVariable long hotelId){
        hotelService.activateHotel(hotelId);
        return ResponseEntity.noContent().build();
    }
    @GetMapping
    @Operation(summary = "Get all hotels by Admin", description = "Retrieve list of all hotels owned by admin")
    public ResponseEntity<List<HotelDTO>> getAllHotels(){
        List<HotelDTO> hotelDTOList = hotelService.getAllHotels();
        return ResponseEntity.ok(hotelDTOList);
    }
    @GetMapping("/{hotelId}/bookings")
    @Operation(summary = "Fetching bookings for a hotel", description = "fetch all bookings by hotel id")
    public ResponseEntity<List<BookingDTO>> getAllBookingByHotelId(@PathVariable long hotelId){
        return ResponseEntity.ok(bookingService.getAllBookingsByHotelId(hotelId));
    }
    @GetMapping("/{hotelId}/reports")
    @Operation(summary = "Generating hotel Report", description = "Generate a hotel Report with hotel id")
    public ResponseEntity<HotelReportDTO> getHotelReport(@PathVariable long hotelId,
                                                         @RequestParam(required = false) LocalDate startDate,
                                                         @RequestParam(required = false) LocalDate endDate){
        if (startDate==null)startDate = LocalDate.now().minusMonths(1);
        if (endDate==null) endDate = LocalDate.now();
        return ResponseEntity.ok(bookingService.getHotelReport(hotelId,startDate,endDate));
    }
}
