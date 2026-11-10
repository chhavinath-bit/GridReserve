package com.gridreserve.booking.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Replaces a venue's editable fields and its weekly hours (FR-CAT-07). {@code version} is the one the
 * client read; if someone saved in between, the update is refused with {@code 409 STALE_UPDATE}
 * (FR-CAT-15). The category can't change: the venue's bookables have types of that category.
 */
public record UpdateVenueRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String addressLine,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "Must be a two-letter ISO country code.") String countryCode,
        @NotBlank @Size(max = 64) String timeZone,
        @NotNull @Size(max = 7) List<@Valid @NotNull OpeningHoursRequest> openingHours) {
}
