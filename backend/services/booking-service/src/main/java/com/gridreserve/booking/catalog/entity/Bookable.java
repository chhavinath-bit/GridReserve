package com.gridreserve.booking.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Something a member books (a room, a seat, a court, a doctor). Rule columns are optional
 * overrides of its type's defaults; null means "inherit" (FR-CAT-04/05).
 */
@Entity
@Table(name = "bookable")
public class Bookable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookable_type_id", nullable = false)
    private BookableType bookableType;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "slot_minutes")
    private Integer slotMinutes;

    @Column(name = "min_duration_minutes")
    private Integer minDurationMinutes;

    @Column(name = "max_duration_minutes")
    private Integer maxDurationMinutes;

    @Column(name = "buffer_minutes")
    private Integer bufferMinutes;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Bookable() {
    }

    /** A new bookable at a venue, open for bookings (FR-CAT-08). The venue is fixed. */
    public static Bookable create(Venue venue, BookableType type, String name, RuleOverrides overrides,
                                  Instant now) {
        Bookable bookable = new Bookable();
        bookable.venue = venue;
        bookable.active = true;
        bookable.createdAt = now;
        bookable.update(type, name, overrides, now);
        return bookable;
    }

    /**
     * Replaces the editable fields. New rules apply to new bookings only; existing bookings keep their stored
     * times (FR-CAT-12). Always marks the row changed, so {@code @Version} goes up (FR-CAT-15).
     */
    public void update(BookableType type, String name, RuleOverrides overrides, Instant now) {
        this.bookableType = type;
        this.name = name;
        this.slotMinutes = overrides.slotMinutes();
        this.minDurationMinutes = overrides.minDurationMinutes();
        this.maxDurationMinutes = overrides.maxDurationMinutes();
        this.bufferMinutes = overrides.bufferMinutes();
        this.updatedAt = now;
    }

    /** Opens or closes the bookable for new bookings (soft close, FR-CAT-10/11); unchanged when already so. */
    public boolean changeActive(boolean active, Instant now) {
        if (this.active == active) {
            return false;
        }
        this.active = active;
        this.updatedAt = now;
        return true;
    }

    /** The bookable's own rule values, null where it inherits from its type. */
    public RuleOverrides overrides() {
        return new RuleOverrides(slotMinutes, minDurationMinutes, maxDurationMinutes, bufferMinutes);
    }

    public Long getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
    }

    public BookableType getBookableType() {
        return bookableType;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public Integer getSlotMinutes() {
        return slotMinutes;
    }

    public Integer getMinDurationMinutes() {
        return minDurationMinutes;
    }

    public Integer getMaxDurationMinutes() {
        return maxDurationMinutes;
    }

    public Integer getBufferMinutes() {
        return bufferMinutes;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
