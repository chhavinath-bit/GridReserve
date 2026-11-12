package com.gridreserve.booking.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A new venue (FR-CAT-07) in one of the listed cities (FR-CAT-16); its country and time zone are the city's
 * (D-48). Weekdays left out of {@code openingHours} are closed (an empty list: closed every day).
 */
public record CreateVenueRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String addressLine,
        @NotNull Long cityId,
        @NotNull @Size(max = 7) List<@Valid @NotNull OpeningHoursRequest> openingHours) {
}
