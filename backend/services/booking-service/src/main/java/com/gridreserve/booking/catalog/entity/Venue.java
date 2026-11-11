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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.ZoneId;

/**
 * A place with bookables (Central Library, Sunrise Turf, ...). Its time zone drives opening hours
 * and display; the database guarantees the zone belongs to the country (fk_venue_country_zone).
 */
@Entity
@Table(name = "venue")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "address_line", length = 200)
    private String addressLine;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(nullable = false)
    private boolean active;

    // Optimistic locking for admin edits (two admins editing the same venue).
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Venue() {
    }

    /** A new venue, open for bookings (FR-CAT-07). The category is fixed: bookable types belong to it. */
    public static Venue create(Category category, String name, String addressLine, String city, String countryCode,
                               String timeZone, Instant now) {
        Venue venue = new Venue();
        venue.category = category;
        venue.active = true;
        venue.createdAt = now;
        venue.update(name, addressLine, city, countryCode, timeZone, now);
        return venue;
    }

    /**
     * Replaces the editable fields. Always marks the row changed, so its {@code @Version} goes up even when
     * only the opening hours (another table) changed, and a second admin's stale edit is refused (FR-CAT-15).
     */
    public void update(String name, String addressLine, String city, String countryCode, String timeZone,
                       Instant now) {
        this.name = name;
        this.addressLine = addressLine;
        this.city = city;
        this.countryCode = countryCode;
        this.timeZone = timeZone;
        this.updatedAt = now;
    }

    /** Opens or closes the venue for new bookings (soft close, FR-CAT-13); unchanged when already so. */
    public boolean changeActive(boolean active, Instant now) {
        if (this.active == active) {
            return false;
        }
        this.active = active;
        this.updatedAt = now;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public ZoneId getTimeZone() {
        return ZoneId.of(timeZone);
    }

    public boolean isActive() {
        return active;
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
