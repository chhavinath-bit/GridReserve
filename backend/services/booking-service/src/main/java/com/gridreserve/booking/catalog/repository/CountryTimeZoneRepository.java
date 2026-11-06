package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.CountryTimeZone;
import com.gridreserve.booking.catalog.entity.CountryTimeZoneId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CountryTimeZoneRepository extends JpaRepository<CountryTimeZone, CountryTimeZoneId> {

    @Query("select distinct c.id.countryCode from CountryTimeZone c order by c.id.countryCode")
    List<String> findCountryCodes();

    List<CountryTimeZone> findByIdCountryCodeOrderByIdTimeZone(String countryCode);
}
