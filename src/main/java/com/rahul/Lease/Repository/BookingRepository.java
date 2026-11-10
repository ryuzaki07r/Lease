package com.rahul.Lease.Repository;

import com.rahul.Lease.Enums.BookingStatus;
import com.rahul.Lease.Models.Booking;
import com.rahul.Lease.Models.Hotel;
import com.rahul.Lease.Models.Room;
import com.rahul.Lease.Models.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByPaymentSessionId(String sessionId);

    List<Booking> findByHotel(Hotel hotel);

    List<Booking> findByHotelAndCreatedAtBetween(Hotel hotel, LocalDateTime startDateTime, LocalDateTime endDateTime);

    List<Booking> findByUser(User user);

    boolean existsByHotel(Hotel hotel);

    boolean existsByRoom(Room room);

    // Every status change goes through this row lock, so the expiry job, webhooks and user actions
    // cannot overwrite each other's status
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.paymentSessionId = :sessionId")
    Optional<Booking> findByPaymentSessionIdForUpdate(@Param("sessionId") String sessionId);

    @Query("SELECT b.id FROM Booking b WHERE b.bookingStatus IN :statuses AND b.createdAt < :createdBefore")
    List<Long> findIdsByStatusInAndCreatedAtBefore(@Param("statuses") Collection<BookingStatus> statuses,
                                                   @Param("createdBefore") LocalDateTime createdBefore);
}
