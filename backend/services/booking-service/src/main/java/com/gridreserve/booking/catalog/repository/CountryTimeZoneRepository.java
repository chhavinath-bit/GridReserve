package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.CountryTimeZone;
import com.gridreserve.booking.catalog.entity.CountryTimeZoneId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryTimeZoneRepository extends JpaRepository<CountryTimeZone, CountryTimeZoneId> {
}
