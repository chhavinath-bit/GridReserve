package com.gridreserve.booking.booking.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional body of {@code POST /api/v1/bookings/{id}/cancel} (FR-BK-04). The reason, from the member or an
 * admin, is shown to the member on the booking (D-49).
 */
public record CancelRequest(@Size(max = 200) String reason) {

    /** The reason without surrounding spaces; blank or missing means no reason (stored as null). */
    public String trimmedReason() {
        return reason == null || reason.isBlank() ? null : reason.strip();
    }
}
