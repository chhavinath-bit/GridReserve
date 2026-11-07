package com.gridreserve.booking.booking.dto;

import jakarta.validation.constraints.Size;

/** Optional body of {@code POST /api/v1/bookings/{id}/cancel} (FR-BK-04). */
public record CancelRequest(@Size(max = 200) String reason) {
}
