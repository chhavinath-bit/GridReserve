package com.gridreserve.booking.catalog.dto;

import java.time.ZoneId;

/**
 * How a booking names its bookable (FR-BK-07): the booking module stores only the ID. The category's
 * cancellation cutoff comes along, so a booking can say until when it may be cancelled (CXL-02).
 */
public record BookableInfo(long bookableId, String name, String label, String venueName, ZoneId zone,
                           int cancellationCutoffMinutes) {
}
