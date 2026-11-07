package com.gridreserve.booking.catalog.service;

/**
 * Open from {@code opensAtMinute} to {@code closesAtMinute}, counted from local midnight in the
 * venue's zone (1440 = 24:00, so "open all day" is 0 to 1440).
 */
public record OpeningInterval(int opensAtMinute, int closesAtMinute) {

    /** "08:00", "24:00": a LocalTime cannot say 24:00, so the API uses text. */
    public static String format(int minuteOfDay) {
        return "%02d:%02d".formatted(minuteOfDay / 60, minuteOfDay % 60);
    }
}
