package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    /** Venues of one category (or all when {@code categoryId} is null); inactive ones only when asked. */
    @Query("""
            select v from Venue v
            where (:categoryId is null or v.category.id = :categoryId)
              and (:includeInactive = true or v.active = true)
            """)
    Page<Venue> search(@Param("categoryId") Long categoryId, @Param("includeInactive") boolean includeInactive,
                       Pageable pageable);
}
