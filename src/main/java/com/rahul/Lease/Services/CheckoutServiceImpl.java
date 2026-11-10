package com.rahul.Lease.Services;

import com.rahul.Lease.Exceptions.PaymentException;
import com.rahul.Lease.Models.Booking;
import com.rahul.Lease.Models.User;
import com.rahul.Lease.Repository.BookingRepository;
import com.rahul.Lease.Services.Interfaces.CheckoutService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutServiceImpl implements CheckoutService {
    // Stripe's minimum session lifetime is 30 minutes
    private static final Duration SESSION_EXPIRY = Duration.ofMinutes(30);

    private final BookingRepository bookingRepository;

    @Override
    public String getCheckoutSession(Booking booking, String successUrl, String failureUrl) {
        log.info("Creating session with with bookingId : {}", booking.getId());
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        try{
            SessionCreateParams sessionParams = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
                    .setCustomerEmail(user.getEmail())
                    .setClientReferenceId(booking.getId().toString())
                    // Stripe sends checkout.session.expired after this, which releases the held rooms
                    .setExpiresAt(Instant.now().plus(SESSION_EXPIRY).getEpochSecond())
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(failureUrl)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("inr")
                                                    .setUnitAmount(booking.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName(booking.getHotel().getName() + " : " + booking.getRoom().getType())
                                                                    .setDescription("Booking Id : " +booking.getId())
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            Session session = Session.create(sessionParams);
            booking.setPaymentSessionId(session.getId());
            bookingRepository.save(booking);

            log.info("session crested successfully for booking with ID: {}", booking.getId());
            return session.getUrl();
        } catch (StripeException e) {
            throw new PaymentException("Could not create payment session for booking " + booking.getId(), e);
        }
    }
}
