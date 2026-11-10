package com.rahul.Lease.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Public search result; exposes HotelDTO rather than the Hotel entity so the owner is never serialized
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HotelPriceResponseDTO {
    private HotelDTO hotel;
    private Double price;
}
