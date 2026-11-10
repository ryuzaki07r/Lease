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
    // Date ranges are half-open [checkIn, checkOut): the checkout night is not booked.
    @Query("""
        SELECT i
        FROM Inventory i
        WHERE i.room.id = :roomId
        AND i.date >= :startDate AND i.date < :endDate
        AND i.closed = false
        AND (i.totalCount - i.bookCount - i.reservedCount) >= :roomsCount
""")
    // WRITE (FOR UPDATE), not READ: two shared locks on the same rows deadlock when both bookings then update them
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> findAndLockAvailableInventory(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("roomsCount") Integer roomsCount
    );

    List<Inventory> findByHotelAndDateBetween(Hotel hotel, LocalDate startDate, LocalDate endDate);

    // Locks every night of an existing booking. No availability or closed filter: a booking that already
    // holds rooms must always be able to confirm, cancel or release them.
    @Query("""
        SELECT i
        FROM Inventory i
        WHERE i.room.id = :roomId
        AND i.date >= :startDate AND i.date < :endDate
   """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> findAndLockBookedInventory(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // The update methods below return the number of nights changed so callers can verify every night was updated
    @Modifying
    @Query("""
        UPDATE Inventory i
        SET i.reservedCount = i.reservedCount - :numberOfRooms,
            i.bookCount = i.bookCount + :numberOfRooms
        WHERE i.room.id = :roomId
        AND i.date >= :startDate AND i.date < :endDate
        AND i.reservedCount >= :numberOfRooms
""")
    int confirmBooking(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("numberOfRooms") int numberOfRooms
    );

    @Modifying
    @Query("""
                UPDATE Inventory i
                SET i.bookCount = i.bookCount - :numberOfRooms
                WHERE i.room.id = :roomId
                  AND i.date >= :startDate AND i.date < :endDate
                  AND i.bookCount >= :numberOfRooms
            """)
    int cancelBooking(@Param("roomId") Long roomId,
                       @Param("startDate") LocalDate startDate,
                       @Param("endDate") LocalDate endDate,
                       @Param("numberOfRooms") int numberOfRooms);

    // Gives back rooms held by a booking that was never paid (expired or abandoned)
    @Modifying
    @Query("""
                UPDATE Inventory i
                SET i.reservedCount = i.reservedCount - :numberOfRooms
                WHERE i.room.id = :roomId
                  AND i.date >= :startDate AND i.date < :endDate
                  AND i.reservedCount >= :numberOfRooms
            """)
    int releaseReservation(@Param("roomId") Long roomId,
                           @Param("startDate") LocalDate startDate,
                           @Param("endDate") LocalDate endDate,
                           @Param("numberOfRooms") int numberOfRooms);

    @Modifying
    @Query("""
                UPDATE Inventory i
                SET i.reservedCount = i.reservedCount + :numberOfRooms
                WHERE i.room.id = :roomId
                  AND i.date >= :startDate AND i.date < :endDate
                  AND (i.totalCount - i.bookCount - i.reservedCount) >= :numberOfRooms
                  AND i.closed = false
            """)
    int initBooking(@Param("roomId") Long roomId,
                     @Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate,
                     @Param("numberOfRooms") int numberOfRooms);

    List<Inventory> findByRoomOrderByDate(Room room);

    @Query("""
                SELECT i
                FROM Inventory i
                WHERE i.room.id = :roomId
                  AND i.date BETWEEN :startDate AND :endDate
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> getInventoryAndLockBeforeUpdate(@Param("roomId") Long roomId,
                                                          @Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate);


    @Modifying
    @Query("""
                UPDATE Inventory i
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
