package com.gridreserve.booking.member.repository;

import com.gridreserve.booking.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * Creates the member row unless it exists. One statement, so parallel first requests of the same
     * user cannot collide: PostgreSQL lets one insert win and the others do nothing. Returns 1 when
     * this call created the row, 0 when it was already there.
     */
    @Modifying
    @Query(value = "INSERT INTO member (id, created_at) VALUES (:id, :now) ON CONFLICT (id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("id") long id, @Param("now") Instant now);
}
