package com.gridreserve.booking.catalog.service;

import java.time.Instant;
import java.util.Collection;

/**
 * What the catalog needs to know about bookings, without depending on the booking module (which depends on
 * the catalog; ARCHITECTURE §3 forbids cycles). The booking module implements it.
 */
public interface ScheduledBookings {

    /** Bookings on these bookables that are still coming: CONFIRMED, or HELD and not run out, and not ended. */
    long activeFuture(Collection<Long> bookableIds, Instant now);
}
