package com.gridreserve.booking.catalog.dto;

import com.gridreserve.booking.catalog.entity.BookingModel;

import java.util.List;

/**
 * One bookable with its effective rules and opening hours. {@code ownOpeningHours} says whether the
 * hours are the bookable's own (they replace the venue's) or the venue's.
 */
public record BookableResponse(
        long id,
        long venueId,
        String venueName,
        String venueTimeZone,
        String name,
        String typeCode,
        String label,
        BookingModel bookingModel,
        boolean active,
        RulesResponse rules,
        List<OpeningHoursResponse> openingHours,
        boolean ownOpeningHours) {
}
