package com.gridreserve.booking.audit.dto;

import com.gridreserve.booking.audit.entity.ActorType;

import java.time.Instant;

/** One line of a booking's history (FR-AUD-03). {@code fromStatus} is null for the creation. */
public record AuditEntryResponse(String fromStatus, String toStatus, ActorType actorType, Long actorUserId,
                                 String reason, Instant occurredAt) {
}
