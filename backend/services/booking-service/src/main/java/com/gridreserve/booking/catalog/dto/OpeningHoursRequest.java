package com.gridreserve.booking.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.DayOfWeek;

/**
 * One open weekday, in the same shape the API returns ({@link OpeningHoursResponse}): MONDAY, "08:00",
 * "22:00". "24:00" may only close a day. That opening is before closing, and each weekday appears once, is
 * checked by the service (FR-CAT-09), because it needs the whole list.
 */
public record OpeningHoursRequest(
        @NotNull DayOfWeek weekday,
        @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "Must be a time from 00:00 to 23:59.")
        String opensAt,
        @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d|24:00", message = "Must be a time from 00:01 to 24:00.")
        String closesAt) {
}
