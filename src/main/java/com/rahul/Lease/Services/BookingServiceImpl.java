package com.rahul.Lease.Services;

import com.rahul.Lease.Dto.BookingDTO;
import com.rahul.Lease.Dto.BookingRequest;
import com.rahul.Lease.Dto.GuestDTO;
import com.rahul.Lease.Dto.HotelReportDTO;
import com.rahul.Lease.Enums.BookingStatus;
import com.rahul.Lease.Exceptions.PaymentException;
import com.rahul.Lease.Exceptions.ResourceNotFoundException;
import com.rahul.Lease.Exceptions.UnauthorizedException;
import com.rahul.Lease.Models.*;
import com.rahul.Lease.Repository.*;
import com.rahul.Lease.Services.Interfaces.BookingService;
import com.rahul.Lease.Services.Interfaces.CheckoutService;
import com.rahul.Lease.Strategy.PricingService;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.rahul.Lease.Utils.AppUtils.getCurrentUser;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    // How long a booking holds rooms before payment is started
    public static final int BOOKING_HOLD_MINUTES = 10;

    private final GuestRepository guestRepository;
    private final ModelMapper modelMapper;
    private final BookingRepository bookingRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;
    private final CheckoutService checkoutService;
    private final PricingService pricingService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    @Transactional
    public BookingDTO initialiseBooking(BookingRequest bookingRequest) {
        log.info("Initialising booking for hotel: {}, room :{}, date {}-{}",
                bookingRequest.getHotelId(), bookingRequest.getRoomId(),
                bookingRequest.getCheckInDate(), bookingRequest.getCheckOutDate());

        Hotel hotel = hotelRepository.findById(bookingRequest.getHotelId()).orElseThrow(
                () -> new ResourceNotFoundException("Hotel not found with id: " + bookingRequest.getHotelId())
        );

        Room room = roomRepository.findById(bookingRequest.getRoomId()).orElseThrow(
                () -> new ResourceNotFoundException("Room not found with id: " + bookingRequest.getRoomId())
        );
        if(!room.getHotel().getId().equals(hotel.getId())){
            throw new ResourceNotFoundException("Room " + room.getId() + " does not belong to hotel " + hotel.getId());
        }
        if(!bookingRequest.getCheckOutDate().isAfter(bookingRequest.getCheckInDate())){
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }

        List<Inventory> inventoryList = inventoryRepository.findAndLockAvailableInventory(
                room.getId(), bookingRequest.getCheckInDate(),
                 bookingRequest.getCheckOutDate(), bookingRequest.getRoomsCount());

        long daysCount = ChronoUnit.DAYS.between(bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate());

        if(inventoryList.size() != daysCount){
            throw new IllegalStateException("Selected room is not available anymore");
        }
        int reservedNights = inventoryRepository.initBooking(room.getId(), bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate(), bookingRequest.getRoomsCount());
        if(reservedNights != daysCount){
            throw new IllegalStateException("Selected room is not available anymore");
        }

        BigDecimal priceForRoom = pricingService.calculateTotalPrice(inventoryList);
        BigDecimal totalPrice = priceForRoom.multiply(BigDecimal.valueOf(bookingRequest.getRoomsCount()));

        Booking booking = Booking.builder()
                .hotel(hotel)
                .room(room)
                .roomsCount(bookingRequest.getRoomsCount())
                .user(getCurrentUser())
                .checkInDate(bookingRequest.getCheckInDate())
                .checkOutDate(bookingRequest.getCheckOutDate())
                .bookingStatus(BookingStatus.RESERVED)
                .amount(totalPrice)
                .build();
        booking = bookingRepository.save(booking);
        return modelMapper.map(booking, BookingDTO.class);
    }

    @Override
    @Transactional
    public BookingDTO addGuests(Long bookingId, List<GuestDTO> guestDtoList) {
        log.info("Adding guests with BookingId : {}",bookingId );

        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id : " +bookingId));
        User user = getCurrentUser();

        if(!user.equals(booking.getUser())){
            throw new UnauthorizedException("There is no booking with the userId : " +user.getId());
        }
        if(hasBookingExpired(booking)){
            throw new IllegalStateException("Booking has already expired");
        }
        if(booking.getBookingStatus() != BookingStatus.RESERVED){
            throw new IllegalStateException("Booking is not under reserved state, can not add guests");
        }
        Set<Guest> guests = guestDtoList.stream()
                .map(dto -> {
                    Guest guest = modelMapper.map(dto, Guest.class);
                    guest.setId(null);
                    guest.setUser(user);
                    return guest;
                })
                .collect(Collectors.toSet());

        guestRepository.saveAll(guests);
        booking.getGuests().addAll(guests);
        booking.setBookingStatus(BookingStatus.GUESTS_ADDED);
        bookingRepository.save(booking);
        return modelMapper.map(booking, BookingDTO.class);
    }

    @Override
    @Transactional
    public String initiatePayments(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id: " +bookingId)
        );
        User user = getCurrentUser();
        if(!user.equals(booking.getUser())){
            throw new UnauthorizedException("Booking does not belong the user with id: " + user.getId());
        }
        if(hasBookingExpired(booking)){
            throw new IllegalStateException("Booking has expired");
        }
        if (booking.getBookingStatus() != BookingStatus.GUESTS_ADDED) {
            throw new IllegalStateException(
                    "Booking is not ready for payment initiation");
        }

        String sessionUrl = checkoutService.getCheckoutSession(booking,
                frontendUrl+"/payments/" +bookingId +"/status",
                frontendUrl+"/payments/" +bookingId +"/status");
        booking.setBookingStatus(BookingStatus.PAYMENT_PENDING);
        bookingRepository.save(booking);
        return sessionUrl;
    }

    @Override
    @Transactional
    public void capturePayment(Event event) {
        switch (event.getType()) {
            case "checkout.session.completed" -> {
                Session session = getSession(event);
                // Delayed payment methods complete the session before the money arrives
                if("paid".equals(session.getPaymentStatus())){
                    confirmPayment(session);
                } else {
                    log.info("Session {} completed but not paid yet, waiting for async payment", session.getId());
                }
            }
            case "checkout.session.async_payment_succeeded" -> confirmPayment(getSession(event));
            case "checkout.session.expired", "checkout.session.async_payment_failed" ->
                    expireUnpaidSession(getSession(event));
            default -> log.warn("Unhandled event type: {}", event.getType());
        }
    }

    // Fails loudly (non-2xx, so Stripe retries) instead of silently dropping the event when the
    // webhook's API version does not match the stripe-java version
    private Session getSession(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject().orElse(null);
        if(stripeObject == null){
            log.warn("Stripe event {} has API version {}, deserializing anyway", event.getId(), event.getApiVersion());
            try {
                stripeObject = deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                throw new IllegalStateException("Could not read Stripe event " + event.getId(), e);
            }
        }
        return (Session) stripeObject;
    }

    private void confirmPayment(Session session) {
        String sessionId = session.getId();
        Booking booking = bookingRepository.findByPaymentSessionIdForUpdate(sessionId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with sessionId :" +sessionId)
        );
        // Stripe delivers events at least once, so the same payment can arrive more than once
        if(booking.getBookingStatus() == BookingStatus.CONFIRMED){
            log.info("Booking {} is already confirmed, ignoring duplicate event", booking.getId());
            return;
        }
        if(booking.getBookingStatus() != BookingStatus.PAYMENT_PENDING){
            log.error("Payment received for booking {} in state {}, refunding",
                    booking.getId(), booking.getBookingStatus());
            refund(session.getPaymentIntent(), booking.getId());
            return;
        }

        inventoryRepository.findAndLockBookedInventory(booking.getRoom().getId(),
                booking.getCheckInDate(), booking.getCheckOutDate());
        int confirmedNights = inventoryRepository.confirmBooking(booking.getRoom().getId(),
                booking.getCheckInDate(), booking.getCheckOutDate(), booking.getRoomsCount());
        if(confirmedNights != nights(booking)){
            throw new IllegalStateException("Could only confirm " + confirmedNights + " of " + nights(booking)
                    + " nights for booking " + booking.getId());
        }

        booking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
        log.info("Booking confirmed successfully for booking ID : {}", booking.getId());
    }

    private void expireUnpaidSession(Session session) {
        bookingRepository.findByPaymentSessionIdForUpdate(session.getId()).ifPresent(booking -> {
            if(booking.getBookingStatus() == BookingStatus.PAYMENT_PENDING){
                expire(booking);
            }
        });
    }

    @Override
    @Transactional
    public void expireBooking(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        // Re-check under the lock: the user may have moved on to payment since the job picked this booking
        if(booking == null || !hasBookingExpired(booking)
                || (booking.getBookingStatus() != BookingStatus.RESERVED
                    && booking.getBookingStatus() != BookingStatus.GUESTS_ADDED)){
            return;
        }
        expire(booking);
    }

    private void expire(Booking booking) {
        inventoryRepository.findAndLockBookedInventory(booking.getRoom().getId(),
                booking.getCheckInDate(), booking.getCheckOutDate());
        int releasedNights = inventoryRepository.releaseReservation(booking.getRoom().getId(),
                booking.getCheckInDate(), booking.getCheckOutDate(), booking.getRoomsCount());
        if(releasedNights != nights(booking)){
            log.warn("Released {} of {} nights for expired booking {}",
                    releasedNights, nights(booking), booking.getId());
        }
        booking.setBookingStatus(BookingStatus.EXPIRED);
        bookingRepository.save(booking);
        log.info("Booking {} expired and its rooms were released", booking.getId());
    }

    @Transactional
    @Override
    public void cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking could not be found with the booking id:"
                +bookingId));
        User user = getCurrentUser();
        if(!user.equals(booking.getUser())){
            throw new UnauthorizedException("Booking does not belong to the user with id: " +user.getId());
        }
        if(booking.getBookingStatus() != BookingStatus.CONFIRMED){
            throw new IllegalStateException("Only confirmed bookings can be cancelled");
        }
        if(!LocalDate.now().isBefore(booking.getCheckInDate())){
            throw new IllegalStateException("Bookings can only be cancelled before the check-in date");
        }

        inventoryRepository.findAndLockBookedInventory(booking.getRoom().getId(), booking.getCheckInDate(),
            booking.getCheckOutDate());
        int releasedNights = inventoryRepository.cancelBooking(booking.getRoom().getId(), booking.getCheckInDate(),
                booking.getCheckOutDate(), booking.getRoomsCount());
        if(releasedNights != nights(booking)){
            throw new IllegalStateException("Could only release " + releasedNights + " of " + nights(booking)
                    + " nights for booking " + bookingId);
        }
        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // Refund last: if Stripe fails, the exception rolls back the cancellation above
        try {
            Session session = Session.retrieve(booking.getPaymentSessionId());
            refund(session.getPaymentIntent(), bookingId);
        } catch (StripeException e) {
            throw new PaymentException("Could not reach the payment provider to refund booking " + bookingId, e);
        }
    }

    private void refund(String paymentIntentId, Long bookingId) {
        RefundCreateParams refundParams = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .build();
        // Same key for the same booking, so a retried request never refunds twice
        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("refund-booking-" + bookingId)
                .build();
        try {
            Refund.create(refundParams, requestOptions);
        } catch (StripeException e) {
            throw new PaymentException("Refund failed for booking " + bookingId, e);
        }
    }

    @Override
    public BookingStatus getBookingStatus(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking not found with id: " +bookingId));
        User user = getCurrentUser();
        if(!user.equals(booking.getUser())){
            throw new UnauthorizedException("Booking does not belong to this user with id: " + user.getId());
        }
        return booking.getBookingStatus();
    }

    @Override
    public List<BookingDTO> getAllBookingsByHotelId(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(
                () -> new ResourceNotFoundException("Hotel not found with the id: " + hotelId));
        User user = getCurrentUser();
        log.info("Getting all the bookings for hotel with Id: {}" ,hotelId);
        if(!user.equals(hotel.getOwner())){
            throw new UnauthorizedException("You are not the owner of the hotel with id: " + hotelId);
        }
        List<Booking> bookings = bookingRepository.findByHotel(hotel);
        return bookings.stream()
                .map( (element) -> modelMapper.map(element , BookingDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public HotelReportDTO getHotelReport(Long hotelId, LocalDate startDate, LocalDate endDate) {
        Hotel hotel = hotelRepository.findById(hotelId).orElseThrow(
                () -> new ResourceNotFoundException("Hotel not found with Id: " + hotelId));
        User user = getCurrentUser();
        log.info("Generating report for hotel id : {}", hotelId);

        if(!user.equals(hotel.getOwner())){
            throw new UnauthorizedException("you are not the owner of the hotel with id: " + hotelId);
        }
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Booking> bookings = bookingRepository.findByHotelAndCreatedAtBetween(hotel, startDateTime, endDateTime);

        Long totalConfirmedBookings = bookings.stream()
                .filter(booking -> booking.getBookingStatus() == BookingStatus.CONFIRMED)
                .count();
        BigDecimal totalRevenueOfConfirmedBookings = bookings.stream()
                .filter(booking -> booking.getBookingStatus() == BookingStatus.CONFIRMED)
                .map(Booking::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgRevenue = totalConfirmedBookings == 0 ? BigDecimal.ZERO :
                totalRevenueOfConfirmedBookings.divide(BigDecimal.valueOf(totalConfirmedBookings),
                        2, RoundingMode.HALF_UP);
        return new HotelReportDTO(totalConfirmedBookings, totalRevenueOfConfirmedBookings, avgRevenue);
    }

    @Override
    public List<BookingDTO> getMyBookings() {
        User user = getCurrentUser();
        return bookingRepository.findByUser(user).stream()
                .map(element -> modelMapper.map(element, BookingDTO.class))
                .collect(Collectors.toList());
    }

    public boolean hasBookingExpired(Booking booking) {
        return booking.getCreatedAt().plusMinutes(BOOKING_HOLD_MINUTES).isBefore(LocalDateTime.now());
    }

    private long nights(Booking booking) {
        return ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
    }
}
