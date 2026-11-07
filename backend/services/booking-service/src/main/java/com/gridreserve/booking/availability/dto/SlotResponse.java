package com.gridreserve.booking.availability.dto;

import java.time.Instant;
import java.time.LocalDateTime;

/** One slot in UTC and in venue time (FR-AV-02). */
public record SlotResponse(Instant start, Instant end, LocalDateTime localStart, LocalDateTime localEnd,
                           SlotStatus status) {
}
