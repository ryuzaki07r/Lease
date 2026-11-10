package com.rahul.Lease.Services;

import com.rahul.Lease.Models.Hotel;
import com.rahul.Lease.Models.HotelMinPrice;
import com.rahul.Lease.Models.Inventory;
import com.rahul.Lease.Repository.HotelMinPriceRepository;
import com.rahul.Lease.Repository.HotelRepository;
import com.rahul.Lease.Repository.InventoryRepository;
import com.rahul.Lease.Strategy.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PricingUpdateService {

    private final HotelRepository hotelRepository;
    private final HotelMinPriceRepository hotelMinPriceRepository;
    private final PricingService pricingService;
    private final InventoryRepository inventoryRepository;
    // Scheduler to update inventory and HotelMinPrice table every hour

    @Scheduled(cron = "0 0 * * * *")
    public void updatePrices(){
        int page =0;
        int batchSize = 100;

        while (true){
            Page<Hotel> hotelPage = hotelRepository.findAll(PageRequest.of(page,batchSize));
            if (hotelPage.isEmpty()){
                break;
            }
            hotelPage.getContent().forEach(this::updateHotelPrices);
            page++;
        }
    }

    public void updateHotelPrices(Hotel hotel){
        log.info("Updating prices for hotel with id: {}", hotel.getId());
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusYears(1);
        List<Inventory> inventoryList = inventoryRepository.findByHotelAndDateBetween(hotel, startDate,endDate);
        updateInventoryPrices(inventoryList);
        updateHotelMinPrice(hotel, inventoryList, startDate, endDate);
    }
    private void updateHotelMinPrice(Hotel hotel, List<Inventory> inventoryList,
                                     LocalDate startDate, LocalDate endDate){
        // Computing minimum price per day for the hotel
        Map<LocalDate, BigDecimal> dailyMinPrices = new HashMap<>();
        for(Inventory inventory : inventoryList){
            LocalDate date = inventory.getDate();
            BigDecimal price = inventory.getPrice();
            dailyMinPrices.merge(date,price,BigDecimal::min);
        }
        List<HotelMinPrice> hotelPrices = new ArrayList<>();
        dailyMinPrices.forEach((date, price)-> {
            HotelMinPrice hotelPrice = hotelMinPriceRepository.findByHotelAndDate(hotel, date)
                    .orElse(new HotelMinPrice(hotel, date));
        hotelPrice.setPrice(price);
        hotelPrices.add(hotelPrice);
        });
        hotelMinPriceRepository.saveAll(hotelPrices);
    }
    public void updateInventoryPrices(List<Inventory> inventoryList){
        inventoryList.forEach(inventory -> {
            BigDecimal dynamicPricing = pricingService.calculateDynamicPricing(inventory);
            inventory.setPrice(dynamicPricing);
        });
        inventoryRepository.saveAll(inventoryList);
    }
}
