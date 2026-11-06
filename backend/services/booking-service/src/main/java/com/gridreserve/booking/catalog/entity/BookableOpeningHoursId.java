package com.gridreserve.booking.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Composite key of {@link BookableOpeningHours}: one row per bookable and ISO weekday. */
@Embeddable
public record BookableOpeningHoursId(
        @Column(name = "bookable_id", nullable = false)
        Long bookableId,

        @Column(name = "iso_weekday", nullable = false)
        short isoWeekday) {
}
