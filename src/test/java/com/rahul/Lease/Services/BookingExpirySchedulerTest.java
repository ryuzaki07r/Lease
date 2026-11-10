package com.rahul.Lease.Services;

import com.rahul.Lease.Enums.BookingStatus;
import com.rahul.Lease.Repository.BookingRepository;
import com.rahul.Lease.Services.Interfaces.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingExpirySchedulerTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingService bookingService;
    @InjectMocks BookingExpiryScheduler scheduler;

    @Test
    void looksForUnpaidBookingsOlderThanTheHold() {
        when(bookingRepository.findIdsByStatusInAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        scheduler.expireStaleBookings();

        verify(bookingRepository).findIdsByStatusInAndCreatedAtBefore(
                eq(List.of(BookingStatus.RESERVED, BookingStatus.GUESTS_ADDED)),
                argThat(cutoff -> {
                    LocalDateTime expected = LocalDateTime.now().minusMinutes(BookingServiceImpl.BOOKING_HOLD_MINUTES);
                    assertThat(cutoff).isBetween(expected.minusSeconds(5), expected.plusSeconds(5));
                    return true;
                }));
    }

    @Test
    void oneFailureDoesNotStopTheRest() {
        when(bookingRepository.findIdsByStatusInAndCreatedAtBefore(any(), any())).thenReturn(List.of(1L, 2L, 3L));
        // lenient: strict stubs would also throw for ids 1 and 3, which the scheduler would swallow
        lenient().doThrow(new RuntimeException("boom")).when(bookingService).expireBooking(2L);

        scheduler.expireStaleBookings();

        verify(bookingService).expireBooking(1L);
        verify(bookingService).expireBooking(2L);
        verify(bookingService).expireBooking(3L);
    }
}
