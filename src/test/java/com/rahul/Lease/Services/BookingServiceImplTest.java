package com.rahul.Lease.Services;

import com.rahul.Lease.Dto.BookingRequest;
import com.rahul.Lease.Dto.HotelReportDTO;
import com.rahul.Lease.Enums.BookingStatus;
import com.rahul.Lease.Enums.Role;
import com.rahul.Lease.Exceptions.PaymentException;
import com.rahul.Lease.Exceptions.ResourceNotFoundException;
import com.rahul.Lease.Exceptions.UnauthorizedException;
import com.rahul.Lease.Models.*;
import com.rahul.Lease.Repository.*;
import com.rahul.Lease.Services.Interfaces.CheckoutService;
import com.rahul.Lease.Strategy.PricingService;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock GuestRepository guestRepository;
    @Mock ModelMapper modelMapper;
    @Mock BookingRepository bookingRepository;
    @Mock HotelRepository hotelRepository;
    @Mock RoomRepository roomRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock CheckoutService checkoutService;
    @Mock PricingService pricingService;
    @InjectMocks BookingServiceImpl bookingService;

    private final LocalDate checkIn = LocalDate.now().plusDays(1);
    private final LocalDate checkOut = LocalDate.now().plusDays(3);
    private User user;
    private Hotel hotel;
    private Room room;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bookingService, "frontendUrl", "http://frontend.test");
        user = user(1L);
        hotel = new Hotel();
        hotel.setId(10L);
        hotel.setOwner(user(2L));
        room = new Room();
        room.setId(20L);
        room.setHotel(hotel);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setRoles(Set.of(Role.GUEST));
        return user;
    }

    private Booking booking(BookingStatus status) {
        return Booking.builder()
                .id(5L).hotel(hotel).room(room).user(user)
                .checkInDate(checkIn).checkOutDate(checkOut).roomsCount(1)
                .bookingStatus(status).amount(BigDecimal.valueOf(2000))
                .paymentSessionId("cs_1").createdAt(LocalDateTime.now())
                .guests(new HashSet<>())
                .build();
    }

    @Nested
    class InitialiseBooking {

        private BookingRequest request(LocalDate in, LocalDate out, int rooms) {
            BookingRequest request = new BookingRequest();
            request.setHotelId(10L);
            request.setRoomId(20L);
            request.setCheckInDate(in);
            request.setCheckOutDate(out);
            request.setRoomsCount(rooms);
            return request;
        }

        @BeforeEach
        void setUp() {
            when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));
            when(roomRepository.findById(20L)).thenReturn(Optional.of(room));
        }

        @Test
        void reservesEachNightAndChargesPerRoom() {
            List<Inventory> nights = List.of(new Inventory(), new Inventory());
            when(inventoryRepository.findAndLockAvailableInventory(20L, checkIn, checkOut, 2)).thenReturn(nights);
            when(inventoryRepository.initBooking(20L, checkIn, checkOut, 2)).thenReturn(2);
            when(pricingService.calculateTotalPrice(nights)).thenReturn(BigDecimal.valueOf(2500));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

            bookingService.initialiseBooking(request(checkIn, checkOut, 2));

            ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(saved.capture());
            assertThat(saved.getValue().getAmount()).isEqualByComparingTo("5000");
            assertThat(saved.getValue().getBookingStatus()).isEqualTo(BookingStatus.RESERVED);
            assertThat(saved.getValue().getUser()).isEqualTo(user);
            assertThat(saved.getValue().getRoomsCount()).isEqualTo(2);
        }

        @Test
        void rejectsRoomOfAnotherHotel() {
            Hotel other = new Hotel();
            other.setId(99L);
            room.setHotel(other);

            assertThatThrownBy(() -> bookingService.initialiseBooking(request(checkIn, checkOut, 1)))
                    .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(inventoryRepository);
        }

        @Test
        void rejectsCheckOutNotAfterCheckIn() {
            assertThatThrownBy(() -> bookingService.initialiseBooking(request(checkIn, checkIn, 1)))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(inventoryRepository);
        }

        @Test
        void rejectsWhenANightIsUnavailable() {
            when(inventoryRepository.findAndLockAvailableInventory(20L, checkIn, checkOut, 1))
                    .thenReturn(List.of(new Inventory()));

            assertThatThrownBy(() -> bookingService.initialiseBooking(request(checkIn, checkOut, 1)))
                    .isInstanceOf(IllegalStateException.class);
            verify(inventoryRepository, never()).initBooking(anyLong(), any(), any(), anyInt());
        }

        @Test
        void rejectsWhenReservationDoesNotCoverEveryNight() {
            when(inventoryRepository.findAndLockAvailableInventory(20L, checkIn, checkOut, 1))
                    .thenReturn(List.of(new Inventory(), new Inventory()));
            when(inventoryRepository.initBooking(20L, checkIn, checkOut, 1)).thenReturn(1);

            assertThatThrownBy(() -> bookingService.initialiseBooking(request(checkIn, checkOut, 1)))
                    .isInstanceOf(IllegalStateException.class);
            verify(bookingRepository, never()).save(any());
        }
    }

    @Nested
    class InitiatePayments {

        @Test
        void movesBookingToPaymentPending() {
            Booking booking = booking(BookingStatus.GUESTS_ADDED);
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking));
            when(checkoutService.getCheckoutSession(booking, "http://frontend.test/payments/5/status",
                    "http://frontend.test/payments/5/status")).thenReturn("https://checkout.test/s");

            assertThat(bookingService.initiatePayments(5L)).isEqualTo("https://checkout.test/s");
            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PAYMENT_PENDING);
        }

        @Test
        void requiresGuestsToBeAdded() {
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking(BookingStatus.RESERVED)));

            assertThatThrownBy(() -> bookingService.initiatePayments(5L)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(checkoutService);
        }

        @Test
        void rejectsExpiredHold() {
            Booking booking = booking(BookingStatus.GUESTS_ADDED);
            booking.setCreatedAt(LocalDateTime.now().minusMinutes(11));
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.initiatePayments(5L)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(checkoutService);
        }

        @Test
        void rejectsOtherUsersBooking() {
            Booking booking = booking(BookingStatus.GUESTS_ADDED);
            booking.setUser(user(3L));
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.initiatePayments(5L)).isInstanceOf(UnauthorizedException.class);
        }
    }

    @Nested
    class CapturePayment {

        private Session session(String paymentStatus) {
            Session session = new Session();
            session.setId("cs_1");
            session.setPaymentStatus(paymentStatus);
            session.setPaymentIntent("pi_1");
            return session;
        }

        private Event event(String type, Session session) {
            Event event = mock(Event.class);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getType()).thenReturn(type);
            lenient().when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            lenient().when(deserializer.getObject()).thenReturn(Optional.of(session));
            return event;
        }

        @Test
        void paidSessionConfirmsBooking() {
            Booking booking = booking(BookingStatus.PAYMENT_PENDING);
            when(bookingRepository.findByPaymentSessionIdForUpdate("cs_1")).thenReturn(Optional.of(booking));
            when(inventoryRepository.confirmBooking(20L, checkIn, checkOut, 1)).thenReturn(2);

            bookingService.capturePayment(event("checkout.session.completed", session("paid")));

            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
            verify(inventoryRepository).findAndLockBookedInventory(20L, checkIn, checkOut);
            verify(bookingRepository).save(booking);
        }

        @Test
        void duplicateEventForConfirmedBookingIsIgnored() {
            when(bookingRepository.findByPaymentSessionIdForUpdate("cs_1"))
                    .thenReturn(Optional.of(booking(BookingStatus.CONFIRMED)));

            bookingService.capturePayment(event("checkout.session.completed", session("paid")));

            verifyNoInteractions(inventoryRepository);
            verify(bookingRepository, never()).save(any());
        }

        @Test
        void completedButUnpaidSessionDoesNothing() {
            bookingService.capturePayment(event("checkout.session.completed", session("unpaid")));

            verifyNoInteractions(bookingRepository, inventoryRepository);
        }

        @Test
        void asyncPaymentSucceededConfirmsBooking() {
            Booking booking = booking(BookingStatus.PAYMENT_PENDING);
            when(bookingRepository.findByPaymentSessionIdForUpdate("cs_1")).thenReturn(Optional.of(booking));
            when(inventoryRepository.confirmBooking(20L, checkIn, checkOut, 1)).thenReturn(2);

            bookingService.capturePayment(event("checkout.session.async_payment_succeeded", session("paid")));

            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void failsWhenNotEveryNightCanBeConfirmed() {
            Booking booking = booking(BookingStatus.PAYMENT_PENDING);
            when(bookingRepository.findByPaymentSessionIdForUpdate("cs_1")).thenReturn(Optional.of(booking));
            when(inventoryRepository.confirmBooking(20L, checkIn, checkOut, 1)).thenReturn(1);

            assertThatThrownBy(() -> bookingService.capturePayment(event("checkout.session.completed", session("paid"))))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PAYMENT_PENDING);
        }

        @Test
        void paymentForExpiredBookingIsRefundedOnce() {
            when(bookingRepository.findByPaymentSessionIdForUpdate("cs_1"))
                    .thenReturn(Optional.of(booking(BookingStatus.EXPIRED)));

            try (MockedStatic<Refund> refund = mockStatic(Refund.class)) {
                bookingService.capturePayment(event("checkout.session.completed", session("paid")));

                ArgumentCaptor<RefundCreateParams> params = ArgumentCaptor.forClass(RefundCreateParams.class);
                ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
                refund.verify(() -> Refund.create(params.capture(), options.capture()));
                assertThat(params.getValue().getPaymentIntent()).isEqualTo("pi_1");
                assertThat(options.getValue().getIdempotencyKey()).isEqualTo("refund-booking-5");
            }
            verifyNoInteractions(inventoryRepository);
        }

        @Test
        void expiredSessionReleasesRooms() {
            Booking booking = booking(BookingStatus.PAYMENT_PENDING);
            when(bookingRepository.findByPaymentSessionIdForUpdate("cs_1")).thenReturn(Optional.of(booking));
            when(inventoryRepository.releaseReservation(20L, checkIn, checkOut, 1)).thenReturn(2);

            bookingService.capturePayment(event("checkout.session.expired", session("unpaid")));

            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.EXPIRED);
            verify(inventoryRepository).releaseReservation(20L, checkIn, checkOut, 1);
        }

        @Test
        void expiredSessionLeavesConfirmedBookingAlone() {
            Booking booking = booking(BookingStatus.CONFIRMED);
            when(bookingRepository.findByPaymentSessionIdForUpdate("cs_1")).thenReturn(Optional.of(booking));

            bookingService.capturePayment(event("checkout.session.async_payment_failed", session("unpaid")));

            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
            verifyNoInteractions(inventoryRepository);
        }

        @Test
        void unknownEventTypeIsIgnored() {
            bookingService.capturePayment(event("customer.created", session("paid")));

            verifyNoInteractions(bookingRepository, inventoryRepository);
        }

        @Test
        void readsEventWithMismatchedApiVersion() throws Exception {
            Event event = event("checkout.session.completed", session("paid"));
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            when(deserializer.getObject()).thenReturn(Optional.empty());
            when(deserializer.deserializeUnsafe()).thenReturn(session("paid"));
            Booking booking = booking(BookingStatus.PAYMENT_PENDING);
            when(bookingRepository.findByPaymentSessionIdForUpdate("cs_1")).thenReturn(Optional.of(booking));
            when(inventoryRepository.confirmBooking(20L, checkIn, checkOut, 1)).thenReturn(2);

            bookingService.capturePayment(event);

            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void unreadableEventFailsSoStripeRetries() throws Exception {
            Event event = event("checkout.session.completed", session("paid"));
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            when(deserializer.getObject()).thenReturn(Optional.empty());
            when(deserializer.deserializeUnsafe())
                    .thenThrow(new EventDataObjectDeserializationException("bad", "{}"));

            assertThatThrownBy(() -> bookingService.capturePayment(event)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class ExpireBooking {

        @Test
        void expiresStaleReservationAndReleasesRooms() {
            Booking booking = booking(BookingStatus.RESERVED);
            booking.setCreatedAt(LocalDateTime.now().minusMinutes(11));
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking));
            when(inventoryRepository.releaseReservation(20L, checkIn, checkOut, 1)).thenReturn(2);

            bookingService.expireBooking(5L);

            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.EXPIRED);
            verify(bookingRepository).save(booking);
        }

        @Test
        void skipsBookingStillWithinHold() {
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking(BookingStatus.RESERVED)));

            bookingService.expireBooking(5L);

            verifyNoInteractions(inventoryRepository);
        }

        @Test
        void skipsBookingThatMovedToPayment() {
            Booking booking = booking(BookingStatus.PAYMENT_PENDING);
            booking.setCreatedAt(LocalDateTime.now().minusMinutes(11));
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking));

            bookingService.expireBooking(5L);

            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PAYMENT_PENDING);
            verifyNoInteractions(inventoryRepository);
        }

        @Test
        void skipsMissingBooking() {
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

            bookingService.expireBooking(5L);

            verifyNoInteractions(inventoryRepository);
        }
    }

    @Nested
    class CancelBooking {

        @Test
        void releasesRoomsThenRefunds() {
            Booking booking = booking(BookingStatus.CONFIRMED);
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking));
            when(inventoryRepository.cancelBooking(20L, checkIn, checkOut, 1)).thenReturn(2);
            Session session = new Session();
            session.setPaymentIntent("pi_1");

            try (MockedStatic<Session> sessions = mockStatic(Session.class);
                 MockedStatic<Refund> refund = mockStatic(Refund.class)) {
                sessions.when(() -> Session.retrieve("cs_1")).thenReturn(session);

                bookingService.cancelBooking(5L);

                ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
                refund.verify(() -> Refund.create(any(RefundCreateParams.class), options.capture()));
                assertThat(options.getValue().getIdempotencyKey()).isEqualTo("refund-booking-5");
            }
            assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.CANCELLED);
        }

        @Test
        void stripeOutageBecomesPaymentException() {
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED)));
            when(inventoryRepository.cancelBooking(20L, checkIn, checkOut, 1)).thenReturn(2);

            try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
                sessions.when(() -> Session.retrieve("cs_1")).thenThrow(new ApiConnectionException("down"));

                assertThatThrownBy(() -> bookingService.cancelBooking(5L)).isInstanceOf(PaymentException.class);
            }
        }

        @Test
        void doesNotRefundWhenRoomsCannotBeReleased() {
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED)));
            when(inventoryRepository.cancelBooking(20L, checkIn, checkOut, 1)).thenReturn(1);

            try (MockedStatic<Session> sessions = mockStatic(Session.class);
                 MockedStatic<Refund> refund = mockStatic(Refund.class)) {
                assertThatThrownBy(() -> bookingService.cancelBooking(5L)).isInstanceOf(IllegalStateException.class);
                sessions.verifyNoInteractions();
                refund.verifyNoInteractions();
            }
        }

        @Test
        void onlyConfirmedBookingsCanBeCancelled() {
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking(BookingStatus.RESERVED)));

            assertThatThrownBy(() -> bookingService.cancelBooking(5L)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(inventoryRepository);
        }

        @Test
        void cannotCancelOnCheckInDay() {
            Booking booking = booking(BookingStatus.CONFIRMED);
            booking.setCheckInDate(LocalDate.now());
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.cancelBooking(5L)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(inventoryRepository);
        }

        @Test
        void cannotCancelSomeoneElsesBooking() {
            Booking booking = booking(BookingStatus.CONFIRMED);
            booking.setUser(user(3L));
            when(bookingRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> bookingService.cancelBooking(5L)).isInstanceOf(UnauthorizedException.class);
        }
    }

    @Nested
    class HotelReport {

        @BeforeEach
        void ownHotel() {
            hotel.setOwner(user);
            when(hotelRepository.findById(10L)).thenReturn(Optional.of(hotel));
        }

        @Test
        void countsOnlyConfirmedBookings() {
            Booking first = booking(BookingStatus.CONFIRMED);
            first.setAmount(new BigDecimal("100.00"));
            Booking second = booking(BookingStatus.CONFIRMED);
            second.setAmount(new BigDecimal("201.00"));
            Booking cancelled = booking(BookingStatus.CANCELLED);
            when(bookingRepository.findByHotelAndCreatedAtBetween(any(), any(), any()))
                    .thenReturn(List.of(first, second, cancelled));

            HotelReportDTO report = bookingService.getHotelReport(10L, LocalDate.now().minusDays(7), LocalDate.now());

            assertThat(report.getBookingCount()).isEqualTo(2);
            assertThat(report.getTotalRevenue()).isEqualByComparingTo("301.00");
            assertThat(report.getAvgRevenue()).isEqualByComparingTo("150.50");
        }

        @Test
        void noBookingsGivesZeroAverage() {
            when(bookingRepository.findByHotelAndCreatedAtBetween(any(), any(), any())).thenReturn(List.of());

            HotelReportDTO report = bookingService.getHotelReport(10L, LocalDate.now().minusDays(7), LocalDate.now());

            assertThat(report.getAvgRevenue()).isEqualByComparingTo("0");
        }
    }

    @Test
    void bookingHoldLastsTenMinutes() {
        Booking booking = booking(BookingStatus.RESERVED);
        booking.setCreatedAt(LocalDateTime.now().minusMinutes(9));
        assertThat(bookingService.hasBookingExpired(booking)).isFalse();
        booking.setCreatedAt(LocalDateTime.now().minusMinutes(11));
        assertThat(bookingService.hasBookingExpired(booking)).isTrue();
    }
}
