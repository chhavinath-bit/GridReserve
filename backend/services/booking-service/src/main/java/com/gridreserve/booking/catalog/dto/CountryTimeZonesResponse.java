package com.gridreserve.booking.catalog.dto;

import java.util.List;

/** The IANA zones of one country (FR-CAT-14); exactly one zone lets the admin UI pre-fill it. */
public record CountryTimeZonesResponse(String countryCode, List<String> timeZones) {
}
