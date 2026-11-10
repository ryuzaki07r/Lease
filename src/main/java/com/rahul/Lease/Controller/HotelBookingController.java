package com.rahul.Lease.Controller;

import com.rahul.Lease.Dto.*;
import com.rahul.Lease.Services.Interfaces.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookings")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Booking Flow", description = "Operations related to booking and payments")
public class HotelBookingController {
    private final BookingService bookingService;

    @PostMapping("/init")
    @Operation(summary = "Initialize a new booking", tags = {"Booking flow"})
    public ResponseEntity<BookingDTO> initialiseBooking(@RequestBody @Valid BookingRequest bookingRequest){
        return ResponseEntity.ok(bookingService.initialiseBooking(bookingRequest));
    }
    @PostMapping("/{bookingId}/addGuests")
    @Operation(summary = "Add guests to a booking", tags = {"Booking Guests"})
    public ResponseEntity<BookingDTO> addGuests(@PathVariable long bookingId,
                                                @RequestBody @NotEmpty List<@Valid GuestDTO> guestDTOList){
        return ResponseEntity.ok(bookingService.addGuests(bookingId, guestDTOList));
    }
    @PostMapping("/{bookingId}/payment")
    @Operation(summary = "initiating payment for a booking", tags = {"Payment flow"})
    public ResponseEntity<BookingPaymentInitResponseDTO> initiatePayment(@PathVariable long bookingId){
        String sessionUrl = bookingService.initiatePayments(bookingId);
        return ResponseEntity.ok(new BookingPaymentInitResponseDTO(sessionUrl));
    }
    @PostMapping("/{bookingId}/cancelBooking")
    @Operation(summary = "Cancel the booking", tags = {"Booking flow"})
    public ResponseEntity<Void> cancelBooking(@PathVariable long bookingId){
        bookingService.cancelBooking(bookingId);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{bookingId}/status")
    @Operation(summary = "Getting a booking status", tags = {"Booking flow"})
    public ResponseEntity<BookingStatusResponseDTO> getBookingStatus(@PathVariable long bookingId){
        return ResponseEntity.ok(new
                BookingStatusResponseDTO(bookingService.getBookingStatus(bookingId)));
    }
}
