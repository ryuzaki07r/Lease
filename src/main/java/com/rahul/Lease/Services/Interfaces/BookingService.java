package com.rahul.Lease.Services.Interfaces;

import com.rahul.Lease.Dto.BookingDTO;
import com.rahul.Lease.Dto.BookingRequest;
import com.rahul.Lease.Dto.GuestDTO;
import com.rahul.Lease.Dto.HotelReportDTO;
import com.rahul.Lease.Enums.BookingStatus;
import com.stripe.model.Event;

import java.time.LocalDate;
import java.util.List;

public interface BookingService {
    BookingDTO initialiseBooking(BookingRequest bookingRequest);

    BookingDTO addGuests(Long bookingId, List<GuestDTO> guestDtoList);

    String initiatePayments(Long bookingId);

    void capturePayment(Event event);

    void expireBooking(Long bookingId);

    void cancelBooking(Long bookingId);

    BookingStatus getBookingStatus(Long bookingId);

    List<BookingDTO> getAllBookingsByHotelId(Long hotelId);

    HotelReportDTO getHotelReport(Long hotelId, LocalDate startDate, LocalDate endDate);

    List<BookingDTO> getMyBookings();
}
