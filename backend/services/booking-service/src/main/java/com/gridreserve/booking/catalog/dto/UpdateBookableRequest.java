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
 * Replaces a bookable's editable fields (FR-CAT-08), like {@link CreateBookableRequest} plus the
 * {@code version} the client read (FR-CAT-15). New rules apply to new bookings only (FR-CAT-12). The venue
 * can't change.
 */
public record UpdateBookableRequest(
        @NotNull Long version,
        @NotNull Long bookableTypeId,
        @NotBlank @Size(max = 100) String name,
        Integer slotMinutes,
        @Positive @Max(1440) Integer minDurationMinutes,
        @Positive @Max(1440) Integer maxDurationMinutes,
        @Min(0) @Max(120) Integer bufferMinutes,
        @Size(max = 7) List<@Valid @NotNull OpeningHoursRequest> openingHours) {
}
