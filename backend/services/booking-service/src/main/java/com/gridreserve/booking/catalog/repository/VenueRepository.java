package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, Long> {
}
