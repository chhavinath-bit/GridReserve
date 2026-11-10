package com.gridreserve.booking.catalog.dto;

/**
 * After activating or deactivating a venue or bookable (FR-CAT-10/11/13). Deactivation is a soft close:
 * {@code activeFutureBookings} counts the HELD and CONFIRMED bookings still scheduled there, which stay
 * valid, so the admin knows what is still coming.
 */
public record ActivationResponse(long id, boolean active, long version, long activeFutureBookings) {
}
