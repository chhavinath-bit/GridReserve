package com.gridreserve.booking.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZoneId;

/**
 * A city venues can be in (FR-CAT-16), seeded by migration V7 (D-48). Reference data: the application
 * never creates or changes cities, so there are no setters. The city's country and zone are copied to
 * each of its venues, and the database keeps the copies equal (fk_venue_city).
 */
@Entity
@Table(name = "city")
public class City {

    @Id
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    protected City() {
    }

    public Long getId() {
        return id;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getName() {
        return name;
    }

    public ZoneId getTimeZone() {
        return ZoneId.of(timeZone);
    }
}
