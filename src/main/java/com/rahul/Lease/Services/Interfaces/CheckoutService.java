package com.rahul.Lease.Services.Interfaces;

import com.rahul.Lease.Models.Booking;

public interface CheckoutService {
    String getCheckoutSession(Booking booking, String successUrl, String failureUrl);

}
