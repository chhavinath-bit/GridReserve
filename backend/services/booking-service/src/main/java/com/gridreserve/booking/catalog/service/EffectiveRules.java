package com.gridreserve.booking.catalog.service;

import com.gridreserve.booking.catalog.entity.BookingModel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The rules that apply to one bookable after every override is resolved (FR-CAT-05). Everything that
 * checks or shows a rule (availability now, booking from M1.5) reads it from here, never from the
 * entities directly. Slot and duration values are null for CAPACITY bookables (Phase 2).
 */
public record EffectiveRules(
        BookingModel bookingModel,
        Integer slotMinutes,
        Integer minDurationMinutes,
        Integer maxDurationMinutes,
        int bufferMinutes,
        int cancellationCutoffMinutes,
        int maxActiveBookingsPerMember,
        int bookingHorizonDays,
        ZoneId zone,
        WeeklyHours hours,
        boolean ownHours) {

    /**
     * The booking horizon in whole local days (D-39, BR-07): today and the next (horizon - 1) days at
     * the venue are bookable; this is the first local date that is not. Availability and booking both
     * use it, so the picker never offers a day the API refuses, or the reverse.
     */
    public LocalDate firstDateBeyondHorizon(Instant now) {
        return LocalDate.ofInstant(now, zone).plusDays(bookingHorizonDays);
    }
}
