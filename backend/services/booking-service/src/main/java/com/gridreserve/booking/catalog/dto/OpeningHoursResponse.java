package com.gridreserve.booking.catalog.dto;

import java.time.DayOfWeek;

/** One open weekday in local time, e.g. MONDAY 08:00 to 22:00 (closesAt may be "24:00"). */
public record OpeningHoursResponse(DayOfWeek weekday, String opensAt, String closesAt) {
}
