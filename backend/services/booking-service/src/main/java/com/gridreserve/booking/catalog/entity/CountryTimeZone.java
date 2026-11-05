package com.gridreserve.booking.catalog.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.ZoneId;

/** One valid (country, IANA zone) pair, seeded from IANA zone.tab (FR-CAT-14). */
@Entity
@Table(name = "country_time_zone")
public class CountryTimeZone {

    @EmbeddedId
    private CountryTimeZoneId id;

    protected CountryTimeZone() {
    }

    public String getCountryCode() {
        return id.countryCode();
    }

    public ZoneId getTimeZone() {
        return ZoneId.of(id.timeZone());
    }
}
