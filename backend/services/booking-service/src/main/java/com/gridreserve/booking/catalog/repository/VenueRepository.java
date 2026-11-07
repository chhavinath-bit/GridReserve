package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    /** Venues of one category (or all when {@code categoryId} is null); inactive ones only when asked. */
    @Query("""
            select v from Venue v
            where (:categoryId is null or v.category.id = :categoryId)
              and (:includeInactive = true or v.active = true)
            """)
    Page<Venue> search(@Param("categoryId") Long categoryId, @Param("includeInactive") boolean includeInactive,
                       Pageable pageable);

    /**
     * Locks the venue of a bookable {@code FOR SHARE} and returns its ID (empty if the bookable doesn't
     * exist). Many holds can share the lock at once; a venue deactivation ({@code FOR UPDATE}) waits for
     * them and they wait for it, so no hold is created after a deactivation commits (CONC-09). The venue
     * comes first in the global lock order (CLAUDE.md §8.3).
     */
    @Query(value = """
            SELECT v.id FROM venue v JOIN bookable b ON b.venue_id = v.id
            WHERE b.id = :bookableId
            FOR SHARE OF v
            """, nativeQuery = true)
    Optional<Long> lockForShareByBookable(@Param("bookableId") long bookableId);
}
