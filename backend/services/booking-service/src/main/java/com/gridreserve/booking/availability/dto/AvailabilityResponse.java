package com.gridreserve.booking.availability.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * A bookable's slots on one local date, with what the UI needs to build the picker without hard-coded
 * values (FR-AV-06). {@code closed} is true on a weekday without opening hours; then {@code slots} is empty.
 */
public record AvailabilityResponse(
        long bookableId,
        LocalDate date,
        String venueTimeZone,
        String label,
        int slotMinutes,
        int minDurationMinutes,
        int maxDurationMinutes,
        int bufferMinutes,
        boolean closed,
        List<SlotResponse> slots) {
}
