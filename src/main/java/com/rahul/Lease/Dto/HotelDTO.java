package com.rahul.Lease.Dto;

import com.rahul.Lease.Models.HotelAddress;
import lombok.Data;

@Data
public class HotelDTO {
    private Long id;
    private String name;
    private String city;
    private String[] photos;
    private String[] amenities;
    private HotelAddress contactInfo;
    private Boolean active;
}
