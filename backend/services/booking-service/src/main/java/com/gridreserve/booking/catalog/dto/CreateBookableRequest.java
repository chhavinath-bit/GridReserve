package com.gridreserve.booking.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A new bookable at a venue (FR-CAT-08). The type must belong to the venue's category. Each rule field
 * is an optional override of the type's default (null = inherit, FR-CAT-04); the combination is checked
 * against the effective values (FR-CAT-09). {@code openingHours} null = the venue's hours; a list
 * replaces them for this bookable.
 */
public record CreateBookableRequest(
        @NotNull Long bookableTypeId,
        @NotBlank @Size(max = 100) String name,
        Integer slotMinutes,
        @Positive @Max(1440) Integer minDurationMinutes,
        @Positive @Max(1440) Integer maxDurationMinutes,
        @Min(0) @Max(120) Integer bufferMinutes,
        @Size(max = 7) List<@Valid @NotNull OpeningHoursRequest> openingHours) {
}
