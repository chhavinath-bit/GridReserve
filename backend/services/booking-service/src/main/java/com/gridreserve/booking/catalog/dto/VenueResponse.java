package com.gridreserve.booking.catalog.dto;

import java.util.List;

/**
 * A venue with its weekly opening hours; weekdays that are missing are closed. {@code version} goes back in
 * an admin's update (FR-CAT-15).
 */
public record VenueResponse(
        long id,
        long categoryId,
        String name,
        String addressLine,
        String city,
        String countryCode,
        String timeZone,
        boolean active,
        long version,
        List<OpeningHoursResponse> openingHours) {
}
