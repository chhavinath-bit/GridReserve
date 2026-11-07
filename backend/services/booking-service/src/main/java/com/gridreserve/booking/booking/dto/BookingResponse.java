package com.gridreserve.booking.booking.dto;

import com.gridreserve.booking.booking.entity.BookingStatus;
import com.gridreserve.booking.booking.entity.CancelledBy;

import java.time.Instant;

/**
 * One booking as every endpoint returns it (FR-BK-07). Names come from the catalog at read time; the
 * booking row stores only IDs. {@code expiresAt} is set only while HELD.
 */
public record BookingResponse(
        long id,
        long bookableId,
        String bookableName,
        String bookableLabel,
        String venueName,
        String venueTimeZone,
        Instant start,
        Instant end,
        BookingStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant cancelledAt,
        CancelledBy cancelledBy) {
}
