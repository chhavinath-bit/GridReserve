package com.gridreserve.booking.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A member's booking of a bookable for [startTime, endTime). Overlaps are prevented by the database
 * (ex_booking_bookable_overlap, ex_booking_member_overlap), not by this class.
 *
 * <p>Member and bookable are plain IDs, not entity references: the booking module uses the catalog
 * only through its service, and status changes are written as conditional UPDATEs (M1.5).
 */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "bookable_id", nullable = false)
    private Long bookableId;

    // CAPACITY seat bookings (Phase 2); null for EXCLUSIVE bookings.
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    // endTime + the bookable's buffer, copied at insert so later rule changes never move existing bookings.
    @Column(name = "occupied_until", nullable = false)
    private Instant occupiedUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    // Set only while HELD (ck_booking_held_expires).
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 10)
    private CancelledBy cancelledBy;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Booking() {
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getBookableId() {
        return bookableId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Instant getOccupiedUntil() {
        return occupiedUntil;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public CancelledBy getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
