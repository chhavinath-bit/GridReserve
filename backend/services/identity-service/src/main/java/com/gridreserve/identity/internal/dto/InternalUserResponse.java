package com.gridreserve.identity.internal.dto;

/** What other services may know about a user (FR-ADM-01): no password hash, no role. */
public record InternalUserResponse(long id, String email, String displayName) {
}
