package com.gridreserve.booking.catalog.dto;

import java.util.List;

/** A venue with its weekly opening hours; weekdays that are missing are closed. */
public record VenueResponse(
        long id,
        long categoryId,
        String name,
        String addressLine,
        String countryCode,
        String timeZone,
        boolean active,
        List<OpeningHoursResponse> openingHours) {
}
