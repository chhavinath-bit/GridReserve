package com.gridreserve.booking.catalog.repository;

import com.gridreserve.booking.catalog.entity.BookableOpeningHours;
import com.gridreserve.booking.catalog.entity.BookableOpeningHoursId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookableOpeningHoursRepository extends JpaRepository<BookableOpeningHours, BookableOpeningHoursId> {

    List<BookableOpeningHours> findByIdBookableId(Long bookableId);

    /**
     * Deletes all of them in one statement, run at once. A derived {@code deleteBy} would be queued and
     * Hibernate runs inserts before deletes when it flushes, so re-adding the same weekday would collide.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from BookableOpeningHours h where h.id.bookableId = :bookableId")
    void deleteAllFor(@Param("bookableId") Long bookableId);
}
