package com.rahul.Lease.Repository;

import com.rahul.Lease.Dto.HotelPriceDTO;
import com.rahul.Lease.Models.Hotel;
import com.rahul.Lease.Models.HotelMinPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
@Repository
public interface HotelMinPriceRepository extends JpaRepository<HotelMinPrice, Long> {

    // A hotel matches only if a single room type has enough free rooms on every night of [startDate, endDate)
    @Query(value = """
        SELECT new com.rahul.Lease.Dto.HotelPriceDTO(i.hotel, AVG(i.price))
        FROM HotelMinPrice i
        WHERE i.hotel.city = :city
        AND i.date >= :startDate AND i.date < :endDate
        AND i.hotel.active = true
        AND EXISTS (
            SELECT 1
            FROM Inventory inv
            WHERE inv.hotel = i.hotel
            AND inv.date >= :startDate AND inv.date < :endDate
            AND inv.closed = false
            AND (inv.totalCount - inv.bookCount - inv.reservedCount) >= :roomsCount
            GROUP BY inv.room
            HAVING COUNT(inv) = :dateCount)
        GROUP BY i.hotel
""",
            countQuery = """
        SELECT COUNT(DISTINCT i.hotel)
        FROM HotelMinPrice i
        WHERE i.hotel.city = :city
        AND i.date >= :startDate AND i.date < :endDate
        AND i.hotel.active = true
        AND EXISTS (
            SELECT 1
            FROM Inventory inv
            WHERE inv.hotel = i.hotel
            AND inv.date >= :startDate AND inv.date < :endDate
            AND inv.closed = false
            AND (inv.totalCount - inv.bookCount - inv.reservedCount) >= :roomsCount
            GROUP BY inv.room
            HAVING COUNT(inv) = :dateCount)
""")
    Page<HotelPriceDTO> findHotelsWithAvailableInventory(
            @Param("city") String city,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("roomsCount") Integer roomsCount,
            @Param("dateCount") Long dateCount,
            Pageable pageable
    );

    Optional<HotelMinPrice> findByHotelAndDate(Hotel hotel, LocalDate date);
}
