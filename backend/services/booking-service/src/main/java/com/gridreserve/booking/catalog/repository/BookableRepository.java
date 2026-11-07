package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.Bookable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
