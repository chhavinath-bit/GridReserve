package com.gridreserve.booking.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A new venue (FR-CAT-07). The time zone must be one of the country's (FR-CAT-14); weekdays left out of
 * {@code openingHours} are closed (an empty list: closed every day).
 */
public record CreateVenueRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String addressLine,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "Must be a two-letter ISO country code.") String countryCode,
        @NotBlank @Size(max = 64) String timeZone,
        @NotNull @Size(max = 7) List<@Valid @NotNull OpeningHoursRequest> openingHours) {
}
