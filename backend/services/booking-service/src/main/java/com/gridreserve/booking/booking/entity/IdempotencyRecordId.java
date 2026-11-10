package com.gridreserve.booking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Composite key of {@link IdempotencyRecord}: keys are unique per member (IDEM-02). */
@Embeddable
public record IdempotencyRecordId(
        @Column(name = "member_id", nullable = false)
        Long memberId,
        @Column(name = "idem_key", nullable = false, length = 64)
        String key) {
}
