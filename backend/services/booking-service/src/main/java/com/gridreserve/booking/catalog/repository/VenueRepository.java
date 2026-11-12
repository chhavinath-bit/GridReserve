package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    /**
     * Venues of one category, country and city (each filter optional: null = any); inactive ones only when asked.
     * Each with its city, in the same query (every list item shows the city's name).
     */
    @EntityGraph(attributePaths = "city")
    @Query("""
            select v from Venue v
            where (:categoryId is null or v.category.id = :categoryId)
              and (cast(:countryCode as string) is null or v.countryCode = :countryCode)
              and (:cityId is null or v.city.id = :cityId)
              and (:includeInactive = true or v.active = true)
            """)
    Page<Venue> search(@Param("categoryId") Long categoryId, @Param("countryCode") String countryCode,
                       @Param("cityId") Long cityId, @Param("includeInactive") boolean includeInactive,
                       Pageable pageable);

    /** One venue with its city in one query. */
    @EntityGraph(attributePaths = "city")
    @Query("select v from Venue v where v.id = :id")
    Optional<Venue> findWithCityById(@Param("id") long id);

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

    /**
     * Locks the venue {@code FOR UPDATE} to open or close it (FR-CAT-13). It waits for holds being created
     * on any of its bookables (they hold it {@code FOR SHARE}), and holds that start later wait for it and
     * then see it inactive (CONC-09). First in the global lock order (CLAUDE.md §8.3).
     */
    @Query(value = "SELECT id FROM venue WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> lockForUpdate(@Param("id") long id);
}
