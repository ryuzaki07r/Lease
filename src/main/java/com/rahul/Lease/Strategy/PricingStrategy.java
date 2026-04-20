package com.rahul.Lease.Strategy;

import com.rahul.Lease.Models.Inventory;

import java.math.BigDecimal;

public interface PricingStrategy {
    BigDecimal calculatePrice(Inventory inventory);
}
