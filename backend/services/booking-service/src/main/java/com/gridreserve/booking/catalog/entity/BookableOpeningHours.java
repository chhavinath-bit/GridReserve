package com.gridreserve.booking.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.DayOfWeek;

/**
 * A bookable's own opening interval on one weekday. If a bookable has any of these rows, they
 * replace its venue's weekly hours entirely (FR-CAT-04).
 */
@Entity
@Table(name = "bookable_opening_hours")
public class BookableOpeningHours {

    @EmbeddedId
    private BookableOpeningHoursId id;

    @Column(name = "opens_at_minute", nullable = false)
    private int opensAtMinute;

    @Column(name = "closes_at_minute", nullable = false)
    private int closesAtMinute;

    protected BookableOpeningHours() {
    }

    public BookableOpeningHours(long bookableId, DayOfWeek day, int opensAtMinute, int closesAtMinute) {
        this.id = new BookableOpeningHoursId(bookableId, (short) day.getValue());
        this.opensAtMinute = opensAtMinute;
        this.closesAtMinute = closesAtMinute;
    }

    public Long getBookableId() {
        return id.bookableId();
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
