package com.gridreserve.booking.catalog.dto;

/** The effective rules of one bookable (FR-CAT-05), so the UI never hard-codes them. */
public record RulesResponse(
        Integer slotMinutes,
        Integer minDurationMinutes,
        Integer maxDurationMinutes,
        int bufferMinutes,
        int cancellationCutoffMinutes,
        int maxActiveBookingsPerMember,
        int bookingHorizonDays) {
}
