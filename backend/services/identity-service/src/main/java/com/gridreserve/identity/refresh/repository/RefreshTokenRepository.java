package com.gridreserve.identity.refresh.repository;

import com.gridreserve.identity.refresh.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Slides the expiry of a still-valid token. One statement checks and extends, so an expired
     * or logged-out token can never be brought back: 1 = valid and extended, 0 = refused.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken t SET t.expiresAt = :newExpiresAt
            WHERE t.tokenHash = :tokenHash AND t.expiresAt > :now""")
    int extend(@Param("tokenHash") String tokenHash, @Param("now") Instant now,
               @Param("newExpiresAt") Instant newExpiresAt);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    /** Idempotent: running it twice, or on two instances at once, simply deletes nothing the second time. */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
