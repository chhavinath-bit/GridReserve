package com.gridreserve.booking.booking.repository;

import com.gridreserve.booking.booking.dto.OccupiedRange;
import com.gridreserve.booking.booking.entity.Booking;
import com.gridreserve.booking.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Only the booking module uses this repository. Every status change is a conditional UPDATE that names
 * the state it expects (CLAUDE.md §8.2): if another request changed the row first, 0 rows match and the
 * caller refuses, so two racing requests can never both win.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Occupied ranges of a bookable's active bookings that overlap {@code [from, to)}. A HELD booking
     * whose hold has run out counts as free even before the expiry job marks it (FR-AV-03, FR-EXP-04).
     */
    @Query("""
            select new com.gridreserve.booking.booking.dto.OccupiedRange(b.startTime, b.occupiedUntil)
            from Booking b
            where b.bookableId = :bookableId
              and b.startTime < :to
              and b.occupiedUntil > :from
              and (b.status = :confirmed or (b.status = :held and b.expiresAt > :now))
            """)
    List<OccupiedRange> findOccupied(@Param("bookableId") long bookableId, @Param("from") Instant from,
                                     @Param("to") Instant to, @Param("now") Instant now,
                                     @Param("confirmed") BookingStatus confirmed,
                                     @Param("held") BookingStatus held);

    /**
     * Locks the run-out holds that would block a new hold (FR-EXP-02): on the same bookable over
     * {@code [start, occupiedUntil)} (BR-10 uses the buffer), or of the same member over {@code [start, end)}
     * (BR-11 does not). One statement, ordered by ID, so two holds sweeping the same rows lock them in the
     * same order and cannot deadlock each other.
     */
    @Query(value = """
            SELECT id FROM booking
            WHERE status = 'HELD' AND expires_at <= :now
              AND ((bookable_id = :bookableId AND session_id IS NULL
                    AND start_time < :occupiedUntil AND occupied_until > :start)
                OR (member_id = :memberId AND start_time < :end AND end_time > :start))
            ORDER BY id
            FOR UPDATE
            """, nativeQuery = true)
    List<Long> lockStaleHolds(@Param("bookableId") long bookableId, @Param("memberId") long memberId,
                              @Param("start") Instant start, @Param("end") Instant end,
                              @Param("occupiedUntil") Instant occupiedUntil, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE booking SET status = 'EXPIRED', expires_at = NULL, updated_at = :now
            WHERE id IN (:ids) AND status = 'HELD' AND expires_at <= :now
            """, nativeQuery = true)
    int expireHolds(@Param("ids") Collection<Long> ids, @Param("now") Instant now);

    /** HELD → CONFIRMED, only while the hold is still valid (FR-BK-03). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE booking SET status = 'CONFIRMED', expires_at = NULL, updated_at = :now
            WHERE id = :id AND status = 'HELD' AND expires_at > :now
            """, nativeQuery = true)
    int confirm(@Param("id") long id, @Param("now") Instant now);

    /**
     * HELD or CONFIRMED → CANCELLED (FR-BK-04). A hold that has run out is not cancelled: it is already
     * free, and the expiry job marks it EXPIRED.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE booking
            SET status = 'CANCELLED', expires_at = NULL, cancelled_at = :now, cancelled_by = :cancelledBy,
                cancel_reason = :reason, updated_at = :now
            WHERE id = :id AND (status = 'CONFIRMED' OR (status = 'HELD' AND expires_at > :now))
            """, nativeQuery = true)
    int cancel(@Param("id") long id, @Param("cancelledBy") String cancelledBy, @Param("reason") String reason,
               @Param("now") Instant now);

    /**
     * The caller's bookings (FR-BK-05); each filter is optional. PostgreSQL cannot tell the type of a
     * bare {@code ? is null}, so the time filters are cast. The sort comes from {@code pageable}.
     */
    @Query("""
            select b from Booking b
            where b.memberId = :memberId
              and (:status is null or b.status = :status)
              and (cast(:from as Instant) is null or b.startTime >= :from)
              and (cast(:to as Instant) is null or b.startTime < :to)
            """)
    Page<Booking> findMine(@Param("memberId") long memberId, @Param("status") BookingStatus status,
                           @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
