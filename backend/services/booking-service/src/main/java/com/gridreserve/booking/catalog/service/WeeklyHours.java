package com.gridreserve.booking.catalog.service;

import java.time.DayOfWeek;
import java.util.Map;
import java.util.Optional;

/** One opening interval per open weekday; a weekday that is missing is closed (FR-CAT-03). */
public record WeeklyHours(Map<DayOfWeek, OpeningInterval> days) {

    public WeeklyHours {
        days = Map.copyOf(days);
    }

    public Optional<OpeningInterval> on(DayOfWeek day) {
        return Optional.ofNullable(days.get(day));
    }
}
