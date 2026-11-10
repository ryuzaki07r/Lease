package com.rahul.Lease.Services;

import com.rahul.Lease.Dto.BookingDTO;
import com.rahul.Lease.Dto.BookingRequest;
import com.rahul.Lease.Dto.GuestDTO;
import com.rahul.Lease.Dto.HotelReportDTO;
import com.rahul.Lease.Enums.BookingStatus;
import com.rahul.Lease.Exceptions.ResourceNotFoundException;
import com.rahul.Lease.Exceptions.UnauthorizedException;
import com.rahul.Lease.Models.*;
import com.rahul.Lease.Repository.*;
import com.rahul.Lease.Services.Interfaces.BookingService;
import com.rahul.Lease.Services.Interfaces.CheckoutService;
import com.rahul.Lease.Strategy.PricingService;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
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
        inventoryRepository.initBooking(room.getId(), bookingRequest.getCheckInDate(),
                bookingRequest.getCheckOutDate(), bookingRequest.getRoomsCount());

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

        Booking booking = bookingRepository.findById(bookingId).orElseThrow(
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
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(
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
        if("checkout.session.completed".equals(event.getType())){
            Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
            if(session == null) return;

            String sessionId = session.getId();
            Booking booking = bookingRepository.findByPaymentSessionId(sessionId).orElseThrow(
                    () -> new ResourceNotFoundException("Booking not found with sessionId :" +sessionId)
            );
            booking.setBookingStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);

            inventoryRepository.findAndLockReservedInventory(booking.getRoom().getId(),
                    booking.getCheckInDate(), booking.getCheckOutDate(), booking.getRoomsCount());

            inventoryRepository.confirmBooking(booking.getRoom().getId(),
                    booking.getCheckInDate(), booking.getCheckOutDate(), booking.getRoomsCount());

            log.info("Booking confirmed successfully for booking ID : {}", booking.getId());
        }
        else{
            log.warn("Unhandled event type: {}", event.getType());
        }
    }

    @Transactional
    @Override
    public void cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(
                () -> new ResourceNotFoundException("Booking could not be found with the booking id:"
                +bookingId));
        User user = getCurrentUser();
        if(!user.equals(booking.getUser())){
            throw new UnauthorizedException("Booking does not belong to the user with id: " +user.getId());
        }
        if(booking.getBookingStatus() != BookingStatus.CONFIRMED){
            throw new IllegalStateException("Only confirmed bookings can be cancelled");
        }
        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        inventoryRepository.findAndLockReservedInventory(booking.getRoom().getId(), booking.getCheckInDate(),
            booking.getCheckOutDate(), booking.getRoomsCount());

        inventoryRepository.cancelBooking(booking.getRoom().getId(), booking.getCheckInDate(),
                booking.getCheckOutDate(), booking.getRoomsCount());

        // handling Refund

        try {
            Session session = Session.retrieve(booking.getPaymentSessionId());
            RefundCreateParams refundParams = RefundCreateParams.builder()
                    .setPaymentIntent(session.getPaymentIntent())
                    .build();


            Refund.create(refundParams);
        } catch (StripeException e) {
            throw new RuntimeException(e);
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
        return booking.getCreatedAt().plusMinutes(10).isBefore(LocalDateTime.now());
    }
}
