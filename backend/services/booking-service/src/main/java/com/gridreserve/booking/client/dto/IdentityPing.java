package com.gridreserve.booking.client.dto;

/**
 * booking-service's own view of identity-service's ping response. The caller owns its client DTOs
 * (tolerant reader): no DTO classes are shared between services.
 */
public record IdentityPing(String service, String instance) {
}
