package com.gridreserve.booking.catalog.dto;

public record VenueSummaryResponse(
        long id,
        long categoryId,
        String name,
        String addressLine,
        long cityId,
        String city,
        String countryCode,
        String timeZone,
        boolean active) {
}
