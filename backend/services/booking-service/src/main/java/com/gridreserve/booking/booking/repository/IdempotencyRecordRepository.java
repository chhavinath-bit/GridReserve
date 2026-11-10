package com.gridreserve.booking.booking.repository;

import com.gridreserve.booking.booking.entity.IdempotencyRecord;
import com.gridreserve.booking.booking.entity.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Every change to an idempotency record is one conditional statement: it names the state it expects, and
 * 0 rows means someone else got there first (the same rule as booking transitions, CLAUDE.md §8.2).
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {

    /** Claims a new key; 0 if the member already used it (the primary key decides, IDEM-02). */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_record (member_id, idem_key, request_hash, state, created_at, locked_until, expires_at)
            VALUES (:memberId, :key, :hash, 'IN_PROGRESS', :now, :lockedUntil, :expiresAt)
            ON CONFLICT (member_id, idem_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("memberId") long memberId, @Param("key") String key, @Param("hash") String hash,
                       @Param("now") Instant now, @Param("lockedUntil") Instant lockedUntil,
                       @Param("expiresAt") Instant expiresAt);

    /** A key older than 24 hours counts as unused (IDEM-07), even before the cleanup job removes it. */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE idempotency_record
            SET request_hash = :hash, state = 'IN_PROGRESS', response_status = NULL, response_body = NULL,
                booking_id = NULL, created_at = :now, locked_until = :lockedUntil, expires_at = :expiresAt
            WHERE member_id = :memberId AND idem_key = :key AND expires_at <= :now
            """, nativeQuery = true)
    int reclaimExpired(@Param("memberId") long memberId, @Param("key") String key, @Param("hash") String hash,
                       @Param("now") Instant now, @Param("lockedUntil") Instant lockedUntil,
                       @Param("expiresAt") Instant expiresAt);

    /** The first request crashed and its lease ran out: one retry takes the key over (IDEM-05). */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE idempotency_record SET locked_until = :lockedUntil
            WHERE member_id = :memberId AND idem_key = :key AND request_hash = :hash
              AND state = 'IN_PROGRESS' AND locked_until <= :now
            """, nativeQuery = true)
    int takeOver(@Param("memberId") long memberId, @Param("key") String key, @Param("hash") String hash,
                 @Param("now") Instant now, @Param("lockedUntil") Instant lockedUntil);

    /** Stores the answer, in the hold's own transaction for a success, so the two can't come apart. */
    @Modifying
    @Query(value = """
            UPDATE idempotency_record
            SET state = 'COMPLETED', response_status = :status, response_body = CAST(:body AS jsonb), booking_id = :bookingId
            WHERE member_id = :memberId AND idem_key = :key AND request_hash = :hash AND state = 'IN_PROGRESS'
            """, nativeQuery = true)
    int complete(@Param("memberId") long memberId, @Param("key") String key, @Param("hash") String hash,
                 @Param("status") int status, @Param("body") String body, @Param("bookingId") Long bookingId);

    /** After a 5xx the key is given back, so the client can retry with it (IDEM-06). */
    @Modifying
    @Query(value = "DELETE FROM idempotency_record WHERE member_id = :memberId AND idem_key = :key AND state = 'IN_PROGRESS'",
            nativeQuery = true)
    int release(@Param("memberId") long memberId, @Param("key") String key);

    /** For the cleanup job: idempotent, so several instances may run it at once. */
    @Modifying
    @Query(value = "DELETE FROM idempotency_record WHERE expires_at <= :now", nativeQuery = true)
    int deleteExpired(@Param("now") Instant now);
}
