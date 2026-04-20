package com.rahul.Lease.Repository;

import com.rahul.Lease.Models.Hotel;
import com.rahul.Lease.Models.Inventory;
import com.rahul.Lease.Models.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    void deleteByRoom(Room room);

    @Query("""
            SELECT h
                FROM Hotel h
                WHERE h.city = :city
                AND NOT EXISTS (
                    SELECT 1
                    FROM Inventory i
                    WHERE i.hotel = h
                    AND i.date BETWEEN :startDate AND :endDate
                    GROUP BY i.date
                    HAVING SUM(i.totalCount - i.bookCount - i.reservedCount) < :roomsCount)
            """)
    Page<Hotel> findHotelsWithAvailableInventory(
            @Param("city") String city,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("roomsCount") Integer roomsCount,
            @Param("dateCount") Long dateCount,
            Pageable pageable
    );
    @Query("""
        SELECT i
        FROM Inventory i
        WHERE i.room.id = :roomId
        AND i.date BETWEEN :startDate AND :endDate
        AND i.closed = false
        AND (i.totalCount - i.bookCount - i.reservedCount) >= :roomsCount
""")
    @Lock(LockModeType.PESSIMISTIC_READ)
    List<Inventory> findAndLockAvailableInventory(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("roomsCount") Integer roomsCount
    );

    List<Inventory> findByHotelAndDateBetween(Hotel hotel, LocalDate startDate, LocalDate endDate);

    @Query("""
        SELECT i
        FROM Inventory
        WHERE i.room.id = :roomId
        AND i.date BETWEEN :startDate AND :endDate
        AND i.closed = false
        AND (i.totalCount - i.bookCount) >= numberOfRooms
   """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> findAndLockReservedInventory(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("numberOfRooms") int numberOfRooms
    );

    @Modifying
    @Query("""
        UPDATE Inventory i
        SET i.reservedCount = i.reservedCount - :numberOfRooms,
            i.bookCount = i.bookCount + :numberOfRooms
        WHERE i.room.id = :roomId
        AND i.date BETWEEN :startDate AND :endDate
        AND i.closed = false
        AND (i.totalCount - i.bookCount) >= :numberOfRooms
        AND i.reservedCount >= :numberOfRooms
""")
    void confirmBooking(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("numberOfRooms") int numberOfRooms
    );

    @Modifying
    @Query("""
                UPDATE InventoryEntity i
                SET i.bookCount = i.bookCount - :numberOfRooms
                WHERE i.room.id = :roomId
                  AND i.date BETWEEN :startDate AND :endDate
                  AND (i.totalCount - i.bookCount) >= :numberOfRooms
                  AND i.closed = false
            """)
    void cancelBooking(@Param("roomId") Long roomId,
                       @Param("startDate") LocalDate startDate,
                       @Param("endDate") LocalDate endDate,
                       @Param("numberOfRooms") int numberOfRooms);

    @Modifying
    @Query("""
                UPDATE Inventory i
                SET i.reservedCount = i.reservedCount + :numberOfRooms
                WHERE i.room.id = :roomId
                  AND i.date BETWEEN :startDate AND :endDate
                  AND (i.totalCount - i.bookCount - i.reservedCount) >= :numberOfRooms
                  AND i.closed = false
            """)
    void initBooking(@Param("roomId") Long roomId,
                     @Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate,
                     @Param("numberOfRooms") int numberOfRooms);

    List<Inventory> findByRoomOrderByDate(Room room);

    @Query("""
                SELECT i
                FROM InventoryEntity i
                WHERE i.room.id = :roomId
                  AND i.date BETWEEN :startDate AND :endDate
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> getInventoryAndLockBeforeUpdate(@Param("roomId") Long roomId,
                                                          @Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate);


    @Modifying
    @Query("""
                UPDATE InventoryEntity i
                SET i.surgeFactor = :surgeFactor,
                    i.closed = :closed
                WHERE i.room.id = :roomId
                  AND i.date BETWEEN :startDate AND :endDate
            """)
    void updateInventory(@Param("roomId") Long roomId,
                         @Param("startDate") LocalDate startDate,
                         @Param("endDate") LocalDate endDate,
                         @Param("closed") boolean closed,
                         @Param("surgeFactor") BigDecimal surgeFactor);

}
