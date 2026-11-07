package com.gridreserve.booking.booking.repository;

import com.gridreserve.booking.booking.dto.OccupiedRange;
import com.gridreserve.booking.booking.entity.Booking;
import com.gridreserve.booking.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Occupied ranges of a bookable's active bookings that overlap {@code [from, to)}. A HELD booking
     * whose hold has run out counts as free even before the expiry job marks it (FR-AV-03, FR-EXP-04).
     */
    @Query("""
            select new com.gridreserve.booking.booking.dto.OccupiedRange(b.startTime, b.occupiedUntil)
            from Booking b
            where b.bookableId = :bookableId
              and b.startTime < :to
              and b.occupiedUntil > :from
              and (b.status = :confirmed or (b.status = :held and b.expiresAt > :now))
            """)
    List<OccupiedRange> findOccupied(@Param("bookableId") long bookableId, @Param("from") Instant from,
                                     @Param("to") Instant to, @Param("now") Instant now,
                                     @Param("confirmed") BookingStatus confirmed,
                                     @Param("held") BookingStatus held);
}
