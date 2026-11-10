package com.gridreserve.booking.catalog.entity;

/**
 * A bookable's own rule values (FR-CAT-04); each null means "use the bookable type's default". The
 * resolver turns them into effective rules (FR-CAT-05).
 */
public record RuleOverrides(Integer slotMinutes, Integer minDurationMinutes, Integer maxDurationMinutes,
                            Integer bufferMinutes) {

    public static final RuleOverrides NONE = new RuleOverrides(null, null, null, null);
}
