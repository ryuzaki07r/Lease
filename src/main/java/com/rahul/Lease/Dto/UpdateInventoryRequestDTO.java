package com.rahul.Lease.Dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateInventoryRequestDTO {
    @NotNull
    private LocalDate startDate;
    @NotNull
    private LocalDate endDate;
    @NotNull
    @Positive
    private BigDecimal surgeFactor;
    @NotNull
    private Boolean closed;
}
