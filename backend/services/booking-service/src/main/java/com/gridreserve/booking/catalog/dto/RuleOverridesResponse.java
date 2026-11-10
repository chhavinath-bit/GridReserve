package com.gridreserve.booking.catalog.dto;

/** A bookable's own rule values; null where it uses its type's default (FR-CAT-04). For editing. */
public record RuleOverridesResponse(Integer slotMinutes, Integer minDurationMinutes, Integer maxDurationMinutes,
                                    Integer bufferMinutes) {
}
