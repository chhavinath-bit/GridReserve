package com.gridreserve.booking.booking.dto;

import com.gridreserve.booking.booking.entity.BookingStatus;
import com.gridreserve.booking.booking.entity.CancelledBy;

import java.time.Instant;

/**
 * One booking as every endpoint returns it (FR-BK-07). Names come from the catalog at read time; the
 * booking row stores only IDs. {@code expiresAt} is set only while HELD. {@code cancellableUntil} is the
 * last moment the owner can cancel (CXL-01/02): the hold's expiry, or start minus the category's cutoff
 * for a confirmed booking; null once that is no longer possible in any case (cancelled, expired).
 * {@code cancelReason} is what the member or the admin wrote when cancelling (shown to the member, D-49).
 */
public record BookingResponse(
        long id,
        long bookableId,
        String bookableName,
        String bookableLabel,
        String venueName,
        String venueCity,
        String venueCountryCode,
        String venueTimeZone,
        Instant start,
        Instant end,
        BookingStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant cancelledAt,
        CancelledBy cancelledBy,
        String cancelReason,
        Instant cancellableUntil) {
}
