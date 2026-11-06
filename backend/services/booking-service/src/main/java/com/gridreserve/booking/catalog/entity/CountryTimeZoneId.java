package com.gridreserve.booking.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Composite key of {@link CountryTimeZone}. */
@Embeddable
public record CountryTimeZoneId(
        @JdbcTypeCode(SqlTypes.CHAR)
        @Column(name = "country_code", nullable = false, length = 2)
        String countryCode,

        @Column(name = "time_zone", nullable = false, length = 64)
        String timeZone) {
}
