package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.booking.dto.OccupiedRange;
import com.gridreserve.booking.booking.entity.BookingStatus;
import com.gridreserve.booking.booking.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The booking module's read API for other modules: which time a bookable has taken. Availability
 * uses this instead of touching the booking table itself (only the booking module owns it).
 */
@Service
public class BookingOccupancy {

    private final BookingRepository bookings;

    public BookingOccupancy(BookingRepository bookings) {
        this.bookings = bookings;
    }

    /** One query, however many slots the caller checks against the result. */
    @Transactional(readOnly = true)
    public List<OccupiedRange> occupied(long bookableId, Instant from, Instant to, Instant now) {
        return bookings.findOccupied(bookableId, from, to, now, BookingStatus.CONFIRMED, BookingStatus.HELD);
    }
}
