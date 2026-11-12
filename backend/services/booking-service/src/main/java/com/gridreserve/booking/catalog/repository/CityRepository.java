package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findByCountryCodeOrderByName(String countryCode);
}
