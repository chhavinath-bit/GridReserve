package com.gridreserve.booking.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * {@code POST /api/v1/bookings} (FR-BK-01). Times are UTC instants (API-03). There is deliberately no
 * member ID: the owner is always the caller (FR-ROLE-05).
 */
public record CreateHoldRequest(
        @NotNull @Positive Long bookableId,
        @NotNull Instant start,
        @NotNull Instant end) {
}
