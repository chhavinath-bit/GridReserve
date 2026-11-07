package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.Bookable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookableRepository extends JpaRepository<Bookable, Long> {

    /** A venue's bookables with their types in the same query (each item shows the type's label). */
    @EntityGraph(attributePaths = "bookableType")
    @Query("""
            select b from Bookable b
            where b.venue.id = :venueId
              and (:includeInactive = true or b.active = true)
            """)
    Page<Bookable> findByVenue(@Param("venueId") Long venueId, @Param("includeInactive") boolean includeInactive,
                               Pageable pageable);

    /** One bookable with everything the rule resolver reads, in one query. */
    @EntityGraph(attributePaths = {"venue", "bookableType", "bookableType.category"})
    @Query("select b from Bookable b where b.id = :id")
    Optional<Bookable> findWithRulesById(@Param("id") Long id);

    /**
     * Locks the bookable {@code FOR UPDATE} for a hold, second in the lock order after its venue (D-41).
     * Exclusive on purpose: holds on the same bookable then insert one after another. With a shared lock,
     * two conflicting inserts could each wait for the other's uncommitted row in the exclusion check and
     * deadlock (found by CONC-01, M1.8). Holds on different bookables are not affected. A deactivation
     * (FOR UPDATE, M1.11) waits for running holds, and holds after it see the bookable inactive (CONC-09).
     */
    @Query(value = "SELECT id FROM bookable WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> lockForBooking(@Param("id") long id);

    /** Several bookables with venue, type and category in one query, for labelling a list of bookings. */
    @EntityGraph(attributePaths = {"venue", "bookableType", "bookableType.category"})
    @Query("select b from Bookable b where b.id in :ids")
    List<Bookable> findWithVenueByIdIn(@Param("ids") Collection<Long> ids);

    /** Every bookable of a category, inactive ones included: their bookings still count (BR-08). */
    @Query("select b.id from Bookable b where b.bookableType.category.id = :categoryId")
    List<Long> findIdsByCategory(@Param("categoryId") long categoryId);
}
