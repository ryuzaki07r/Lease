package com.rahul.Lease.Controller;

import com.rahul.Lease.Dto.BookingDTO;
import com.rahul.Lease.Dto.RoomDTO;
import com.rahul.Lease.Services.Interfaces.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/hotels/{hotelId}/rooms")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Admin Room Management", description = "rooms/hotel/inventory managed by admin")
public class RoomAdminController {

    private final RoomService roomService;

    @PostMapping
    @Operation(summary = "Create a new room", description = "Adding a new room to a specific hotel")
    public ResponseEntity<RoomDTO> createNewRoom(@PathVariable Long hotelId, @RequestBody @Valid RoomDTO roomDTO){
        return ResponseEntity.ok(roomService.createNewRoom(hotelId,roomDTO));
    }

    @GetMapping
    @Operation(summary = "Retrieve all rooms in hotel", description = "Fetches all rooms corresponding to a hotel")
    public ResponseEntity<List<RoomDTO>> getAllRoomsInHotel(@PathVariable Long hotelId){
        return ResponseEntity.ok(roomService.getAllRoomsInHotel(hotelId));
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "Fetches specific room", description = "Retrieves specific room from a specific hotel")
    public ResponseEntity<RoomDTO> getRoomById(@PathVariable Long hotelId, @PathVariable Long roomId){
        return ResponseEntity.ok(roomService.getRoomById(hotelId, roomId));
    }
    @DeleteMapping("/{roomId}")
    @Operation(summary = "Deletes a specific room", description = "Deletes a specific room in a hotel")
    public ResponseEntity<Void> deleteRoomById(@PathVariable Long hotelId, @PathVariable Long roomId){
        roomService.deleteRoomById(roomId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{roomId}")
    @Operation(summary = "Update Room", description = "Updates details of an existing room in a hotel")
    public ResponseEntity<RoomDTO> updateRoomById(@PathVariable Long hotelId,
                                                  @PathVariable Long roomId,
                                                  @RequestBody @Valid RoomDTO roomDTO){
        return ResponseEntity.ok(roomService.updateRoomById(hotelId,roomId, roomDTO));
    }
}
