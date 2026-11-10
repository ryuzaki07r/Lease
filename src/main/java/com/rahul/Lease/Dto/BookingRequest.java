package com.rahul.Lease.Dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BookingRequest {
    @NotNull
    private Long hotelId;
    @NotNull
    private Long roomId;
    @NotNull
    @FutureOrPresent
    private LocalDate checkInDate;
    @NotNull
    private LocalDate checkOutDate;
    @NotNull
    @Min(1)
    private Integer roomsCount;
}
