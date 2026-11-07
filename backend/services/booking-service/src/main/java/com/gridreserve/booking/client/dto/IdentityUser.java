package com.gridreserve.booking.client.dto;

/** booking-service's own view of a user from identity-service (tolerant reader, ARCHITECTURE §3). */
public record IdentityUser(long id, String email, String displayName) {
}
