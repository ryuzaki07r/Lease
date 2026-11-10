package com.rahul.Lease.Services;

import com.rahul.Lease.Dto.HotelSearchRequest;
import com.rahul.Lease.Models.Hotel;
import com.rahul.Lease.Models.Inventory;
import com.rahul.Lease.Models.Room;
import com.rahul.Lease.Repository.HotelMinPriceRepository;
import com.rahul.Lease.Repository.InventoryRepository;
import com.rahul.Lease.Repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock InventoryRepository inventoryRepository;
    @Mock ModelMapper modelMapper;
    @Mock RoomRepository roomRepository;
    @Mock HotelMinPriceRepository hotelMinPriceRepository;
    @InjectMocks InventoryServiceImpl inventoryService;

    private HotelSearchRequest search(LocalDate start, LocalDate end) {
        HotelSearchRequest request = new HotelSearchRequest();
        request.setCity("Pune");
        request.setStartDate(start);
        request.setEndDate(end);
        request.setRoomsCount(1);
        return request;
    }

    @Test
    void searchCountsNightsNotDays() {
        LocalDate start = LocalDate.now().plusDays(1);
        when(hotelMinPriceRepository.findHotelsWithAvailableInventory(any(), any(), any(), anyInt(), anyLong(), any()))
                .thenReturn(Page.empty());

        inventoryService.searchHotels(search(start, start.plusDays(2)));

        // Checkout day is not a night: two nights must be available, not three
        verify(hotelMinPriceRepository).findHotelsWithAvailableInventory(
                eq("Pune"), eq(start), eq(start.plusDays(2)), eq(1), eq(2L), any(Pageable.class));
    }

    @Test
    void searchRejectsEmptyStay() {
        LocalDate start = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> inventoryService.searchHotels(search(start, start)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inventoryService.searchHotels(search(start, start.minusDays(1))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(hotelMinPriceRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void newRoomGetsAYearOfOpenInventory() {
        Hotel hotel = new Hotel();
        hotel.setCity("Pune");
        Room room = new Room();
        room.setHotel(hotel);
        room.setBasePrice(new BigDecimal("1000"));
        room.setTotalCount(3);

        inventoryService.initializeRoomForAYear(room);

        ArgumentCaptor<List<Inventory>> saved = ArgumentCaptor.forClass(List.class);
        verify(inventoryRepository).saveAll(saved.capture());
        List<Inventory> nights = saved.getValue();
        assertThat(nights).hasSizeGreaterThanOrEqualTo(365);
        assertThat(nights.get(0).getDate()).isEqualTo(LocalDate.now());
        assertThat(nights).allSatisfy(night -> {
            assertThat(night.getTotalCount()).isEqualTo(3);
            assertThat(night.getBookCount()).isZero();
            assertThat(night.getReservedCount()).isZero();
            assertThat(night.getClosed()).isFalse();
            assertThat(night.getCity()).isEqualTo("Pune");
        });
    }
}
