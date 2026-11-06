package com.gridreserve.booking.catalog.service;

import com.gridreserve.booking.catalog.entity.BookingModel;

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
}
