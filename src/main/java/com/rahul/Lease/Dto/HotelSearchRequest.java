package com.rahul.Lease.Dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class HotelSearchRequest {
    @NotBlank
    private String city;
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    @NotNull
    @Min(1)
    private Integer roomsCount;
    @Min(0)
    private Integer page=0;
    @Min(1)
    @Max(100)
    private Integer size=10;
}
