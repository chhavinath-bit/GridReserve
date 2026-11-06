package com.gridreserve.booking.availability.service;

import com.gridreserve.booking.catalog.service.OpeningInterval;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a day's slots in the venue's local time, then turns each into UTC instants. Pure Java, no
 * database, so every edge can be unit-tested.
 *
 * <p>Slots start at the opening time and step by the slot length; the last one ends at or before
 * closing (BR-03: the grid is anchored at that day's opening time).
 *
 * <p>Daylight saving time: on the night the clocks jump forward, a local start that does not exist
 * (e.g. 01:30 in London on the last Sunday of March) produces no slot. On the night they go back, a
 * local time that happens twice uses its first occurrence, so the grid still has one slot per local
 * start time.
 */
public final class LocalSlotGrid {

    private LocalSlotGrid() {
    }

    public static List<LocalSlot> slots(LocalDate date, OpeningInterval hours, int slotMinutes, ZoneId zone) {
        LocalDateTime midnight = date.atStartOfDay();
        List<LocalSlot> slots = new ArrayList<>();
        for (int minute = hours.opensAtMinute(); minute + slotMinutes <= hours.closesAtMinute();
             minute += slotMinutes) {
            LocalDateTime localStart = midnight.plusMinutes(minute);
            if (zone.getRules().getValidOffsets(localStart).isEmpty()) {
                continue; // skipped by a daylight-saving jump
            }
            ZonedDateTime start = ZonedDateTime.of(localStart, zone);
            ZonedDateTime end = ZonedDateTime.of(localStart.plusMinutes(slotMinutes), zone);
            slots.add(new LocalSlot(start.toInstant(), end.toInstant(),
                    start.toLocalDateTime(), end.toLocalDateTime()));
        }
        return slots;
    }
}
