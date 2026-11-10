package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.booking.entity.BookingStatus;
import com.gridreserve.booking.booking.repository.BookingRepository;
import com.gridreserve.booking.catalog.service.ScheduledBookings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;

/** The booking module's answer to the catalog's question "what is still scheduled here?" (FR-CAT-10). */
@Service
class ScheduledBookingCounter implements ScheduledBookings {

    private final BookingRepository bookings;

    ScheduledBookingCounter(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Override
    @Transactional(readOnly = true)
    public long activeFuture(Collection<Long> bookableIds, Instant now) {
        if (bookableIds.isEmpty()) {
            return 0;
        }
        return bookings.countScheduled(bookableIds, now, BookingStatus.CONFIRMED, BookingStatus.HELD);
    }
}
