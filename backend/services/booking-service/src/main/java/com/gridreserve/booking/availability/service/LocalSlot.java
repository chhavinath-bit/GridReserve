package com.gridreserve.booking.availability.service;

import java.time.Instant;
import java.time.LocalDateTime;

/** One slot: when it is (UTC) and how it reads on the venue's clock. */
public record LocalSlot(Instant start, Instant end, LocalDateTime localStart, LocalDateTime localEnd) {
}
