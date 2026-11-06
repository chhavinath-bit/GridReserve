package com.gridreserve.booking.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * booking-service's local copy of an identity user: the ID only, no personal data. It gives the
 * per-member limit a row to lock (BR-08) and keeps booking working while identity-service is down.
 */
@Entity
@Table(name = "member")
public class Member {

    // The identity user ID (JWT subject), not generated here.
    @Id
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Member() {
    }

    public Member(Long id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
