package com.gridreserve.booking.booking.service;

/**
 * The answer to {@code POST /bookings}: a new hold, or the stored answer to an earlier request with the same
 * {@code Idempotency-Key} ({@code replayed}, IDEM-03). {@code body} is the booking as JSON-ready object.
 */
public record HoldResult(int status, Object body, long bookingId, boolean replayed) {
}
