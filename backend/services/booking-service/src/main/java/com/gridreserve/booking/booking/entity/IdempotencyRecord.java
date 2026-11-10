package com.gridreserve.booking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * What happened to one {@code Idempotency-Key} of one member on {@code POST /bookings} (IDEM-01..07). Read
 * through JPA; every change is a conditional native statement in {@code IdempotencyRecordRepository}, so two
 * requests racing for the same key can't both win.
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";

    @EmbeddedId
    private IdempotencyRecordId id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, length = 12)
    private String state;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "locked_until", nullable = false)
    private Instant lockedUntil;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public String getRequestHash() {
        return requestHash;
    }

    public boolean isCompleted() {
        return COMPLETED.equals(state);
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
