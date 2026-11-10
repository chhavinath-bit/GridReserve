package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.VenueOpeningHours;
import com.gridreserve.booking.catalog.entity.VenueOpeningHoursId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VenueOpeningHoursRepository extends JpaRepository<VenueOpeningHours, VenueOpeningHoursId> {

    List<VenueOpeningHours> findByIdVenueId(Long venueId);

    /**
     * Deletes all of them in one statement, run at once. A derived {@code deleteBy} would be queued and
     * Hibernate runs inserts before deletes when it flushes, so re-adding the same weekday would collide.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from VenueOpeningHours h where h.id.venueId = :venueId")
    void deleteAllFor(@Param("venueId") Long venueId);
}
