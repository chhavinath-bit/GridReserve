package com.gridreserve.booking.catalog.dto;

import com.gridreserve.booking.catalog.service.EffectiveRules;

/** What other modules (availability, booking) get from the catalog about one bookable. */
public record BookableSchedule(long bookableId, String label, EffectiveRules rules) {
}
