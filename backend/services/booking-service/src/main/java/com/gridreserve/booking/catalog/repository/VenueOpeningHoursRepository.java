package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.VenueOpeningHours;
import com.gridreserve.booking.catalog.entity.VenueOpeningHoursId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueOpeningHoursRepository extends JpaRepository<VenueOpeningHours, VenueOpeningHoursId> {
}
