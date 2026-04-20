package com.rahul.Lease.Models;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class HotelAddress {
    private String address;
    private String phoneNumber;
    private String email;
    private String location;
}
