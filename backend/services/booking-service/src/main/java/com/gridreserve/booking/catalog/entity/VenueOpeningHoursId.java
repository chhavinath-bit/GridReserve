package com.gridreserve.booking.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Composite key of {@link VenueOpeningHours}: one row per venue and ISO weekday. */
@Embeddable
public record VenueOpeningHoursId(
        @Column(name = "venue_id", nullable = false)
        Long venueId,

        @Column(name = "iso_weekday", nullable = false)
        short isoWeekday) {
}
