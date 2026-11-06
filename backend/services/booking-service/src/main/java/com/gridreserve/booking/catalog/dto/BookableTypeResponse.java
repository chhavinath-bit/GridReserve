package com.gridreserve.booking.catalog.dto;

import com.gridreserve.booking.catalog.entity.BookingModel;

/** A bookable type and its default rules; slot and durations are null for CAPACITY types. */
public record BookableTypeResponse(
        long id,
        String code,
        String label,
        BookingModel bookingModel,
        Integer slotMinutes,
        Integer minDurationMinutes,
        Integer maxDurationMinutes,
        int bufferMinutes) {
}
