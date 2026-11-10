package com.rahul.Lease.Strategy;

import com.rahul.Lease.Models.Inventory;
import com.rahul.Lease.Models.Room;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private final PricingService pricingService = new PricingService();

    private static Inventory night(LocalDate date, String surge, int booked, int total) {
        Room room = new Room();
        room.setBasePrice(new BigDecimal("1000"));
        return Inventory.builder()
                .room(room).date(date).surgeFactor(new BigDecimal(surge))
                .bookCount(booked).totalCount(total)
                .build();
    }

    private final LocalDate farAway = LocalDate.now().plusDays(30);

    @Test
    void basePriceWhenNothingApplies() {
        assertThat(pricingService.calculateDynamicPricing(night(farAway, "1", 0, 10)))
                .isEqualByComparingTo("1000");
    }

    @Test
    void surgeFactorMultipliesPrice() {
        assertThat(pricingService.calculateDynamicPricing(night(farAway, "1.5", 0, 10)))
                .isEqualByComparingTo("1500");
    }

    @Test
    void highOccupancyAddsTwelvePercent() {
        assertThat(pricingService.calculateDynamicPricing(night(farAway, "1", 8, 10)))
                .isEqualByComparingTo("1000");
        assertThat(pricingService.calculateDynamicPricing(night(farAway, "1", 9, 10)))
                .isEqualByComparingTo("1120");
    }

    @Test
    void nightsWithinAWeekAddTenPercent() {
        assertThat(pricingService.calculateDynamicPricing(night(LocalDate.now(), "1", 0, 10)))
                .isEqualByComparingTo("1100");
        assertThat(pricingService.calculateDynamicPricing(night(LocalDate.now().plusDays(6), "1", 0, 10)))
                .isEqualByComparingTo("1100");
        assertThat(pricingService.calculateDynamicPricing(night(LocalDate.now().plusDays(7), "1", 0, 10)))
                .isEqualByComparingTo("1000");
    }

    @Test
    void strategiesCombine() {
        // 1000 * 2 (surge) * 1.12 (occupancy) * 1.10 (urgency)
        assertThat(pricingService.calculateDynamicPricing(night(LocalDate.now().plusDays(1), "2", 9, 10)))
                .isEqualByComparingTo("2464");
    }

    @Test
    void totalIsSumOfNights() {
        assertThat(pricingService.calculateTotalPrice(List.of(
                night(farAway, "1", 0, 10),
                night(farAway.plusDays(1), "1.5", 0, 10))))
                .isEqualByComparingTo("2500");
        assertThat(pricingService.calculateTotalPrice(List.of())).isEqualByComparingTo("0");
    }
}
