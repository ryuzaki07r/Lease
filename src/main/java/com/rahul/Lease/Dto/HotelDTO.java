package com.rahul.Lease.Dto;

import com.rahul.Lease.Models.HotelAddress;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HotelDTO {
    private Long id;
    @NotBlank
    private String name;
    @NotBlank
    private String city;
    private String[] photos;
    private String[] amenities;
    private HotelAddress contactInfo;
    private Boolean active;
}
