package com.rahul.Lease.Services;

import com.rahul.Lease.Enums.BookingStatus;
import com.rahul.Lease.Repository.BookingRepository;
import com.rahul.Lease.Services.Interfaces.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// Releases rooms held by bookings that were abandoned before payment started.
// Bookings already sent to Stripe are expired by the checkout.session.expired webhook instead.
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @Scheduled(fixedDelayString = "${booking.expiry.check-interval-ms:60000}")
    public void expireStaleBookings(){
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(BookingServiceImpl.BOOKING_HOLD_MINUTES);
        List<Long> bookingIds = bookingRepository.findIdsByStatusInAndCreatedAtBefore(
                List.of(BookingStatus.RESERVED, BookingStatus.GUESTS_ADDED), cutoff);

        // One transaction per booking, so one failure does not stop the rest
        for(Long bookingId : bookingIds){
            try {
                bookingService.expireBooking(bookingId);
            } catch (Exception e) {
                log.error("Failed to expire booking {}", bookingId, e);
            }
        }
    }
}
