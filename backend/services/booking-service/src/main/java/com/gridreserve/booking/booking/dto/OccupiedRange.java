package com.gridreserve.booking.booking.dto;

import java.time.Instant;

/**
 * The time an active booking blocks: {@code [start, until)}, where {@code until} is the end plus the
 * buffer. Deliberately nothing about who booked it (FR-AV-05).
 */
public record OccupiedRange(Instant start, Instant until) {

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && until.isAfter(otherStart);
    }
}
