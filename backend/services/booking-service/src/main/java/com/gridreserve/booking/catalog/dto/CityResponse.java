package com.gridreserve.booking.catalog.dto;

/** A city an admin can put a venue in (FR-CAT-16); its zone becomes the venue's (D-48). */
public record CityResponse(long id, String name, String countryCode, String timeZone) {
}
