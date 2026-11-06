package com.gridreserve.booking.catalog.dto;

import com.gridreserve.booking.catalog.entity.BookingModel;

/** A list item: the label ("Room", "Court") comes from the bookable type, never from code. */
public record BookableSummaryResponse(
        long id,
        long venueId,
        String name,
        String typeCode,
        String label,
        BookingModel bookingModel,
        boolean active) {
}
