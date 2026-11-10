package com.gridreserve.booking.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.DayOfWeek;

/**
 * A venue's opening interval on one weekday, in minutes from local midnight in the venue's zone
 * (closes at 1440 = 24:00). No row for a weekday means closed.
 */
@Entity
@Table(name = "venue_opening_hours")
public class VenueOpeningHours {

    @EmbeddedId
    private VenueOpeningHoursId id;

    @Column(name = "opens_at_minute", nullable = false)
    private int opensAtMinute;

    @Column(name = "closes_at_minute", nullable = false)
    private int closesAtMinute;

    protected VenueOpeningHours() {
    }

    public VenueOpeningHours(long venueId, DayOfWeek day, int opensAtMinute, int closesAtMinute) {
        this.id = new VenueOpeningHoursId(venueId, (short) day.getValue());
        this.opensAtMinute = opensAtMinute;
        this.closesAtMinute = closesAtMinute;
    }

    public Long getVenueId() {
        return id.venueId();
    }

    public DayOfWeek getDayOfWeek() {
        return DayOfWeek.of(id.isoWeekday());
    }

    public int getOpensAtMinute() {
        return opensAtMinute;
    }

    public int getClosesAtMinute() {
        return closesAtMinute;
    }
}
