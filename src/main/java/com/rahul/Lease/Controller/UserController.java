package com.rahul.Lease.Controller;

import com.rahul.Lease.Dto.BookingDTO;
import com.rahul.Lease.Dto.GuestDTO;
import com.rahul.Lease.Dto.ProfileUpdateRequestDTO;
import com.rahul.Lease.Dto.UserDTO;
import com.rahul.Lease.Services.Interfaces.BookingService;
import com.rahul.Lease.Services.Interfaces.GuestService;
import com.rahul.Lease.Services.Interfaces.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Manages user profile and bookings")
@SecurityRequirement(name = "BearerAuth")
public class UserController {
    private final UserService userService;
    private final BookingService bookingService;
    private final GuestService guestService;

    @PatchMapping("/profile")
    @Operation(summary = "Profile update", description = "Allows user to update the profile", tags = {"User Profile"})
    public ResponseEntity<Void> updateProfile(@RequestBody ProfileUpdateRequestDTO profileUpdateRequestDTO){
        userService.updateProfile(profileUpdateRequestDTO);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/myBookings")
    @Operation(summary = "Get my bookings", description = "Fetches all past bookings for user", tags = {"User Profile"})
    public ResponseEntity<List<BookingDTO>> getMyBookings(){
        return ResponseEntity.ok(bookingService.getMyBookings());
    }
    @GetMapping("/profile")
    @Operation(summary = "Fetches profile", description = "Fetches user profile", tags = {"User profile"})
    public ResponseEntity<UserDTO> getMyProfile(){
        return ResponseEntity.ok(userService.getMyProfile());
    }

    @GetMapping("/guests")
    @Operation(summary = "", description = "", tags = {"Booking Guest"})
    public ResponseEntity<List<GuestDTO>> getAllGuests(){
        return ResponseEntity.ok(guestService.getAllGuests());
    }

    @PostMapping("/guests")
    @Operation(summary = "Add new Guest", description = "Adding a new guest to the user's guest list", tags = {"Booking Guest"})
    public ResponseEntity<GuestDTO> addNewGuest(@RequestBody GuestDTO guestDTO){
        return ResponseEntity.status(HttpStatus.CREATED).body(guestService.addNewGuest(guestDTO));
    }

    @PutMapping("/guests/{guestId}")
    @Operation(summary = "Update a Guest", description = "U[dates a guest in user's guest List", tags = {"Booking Guests"})
    public ResponseEntity<Void> updateGuest(@PathVariable Long guestId, @RequestBody GuestDTO guestDTO){
        guestService.updateGuest(guestId, guestDTO);
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/guests/{guestId}")
    @Operation(summary = "Remove a Guest", description = "Removes a guest from guest's list", tags = {"Booking Guest"})
    public ResponseEntity<Void> deleteGuest(@PathVariable Long guestId){
        guestService.deleteGuest(guestId);
        return ResponseEntity.noContent().build();
    }
}
