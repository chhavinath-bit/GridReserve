package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.VenueOpeningHours;
import com.gridreserve.booking.catalog.entity.VenueOpeningHoursId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueOpeningHoursRepository extends JpaRepository<VenueOpeningHours, VenueOpeningHoursId> {

    List<VenueOpeningHours> findByIdVenueId(Long venueId);
}
