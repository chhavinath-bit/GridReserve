package com.gridreserve.identity.user.dto;

/** A user as the API shows it: never the password hash (FR-AUTH-01, FR-AUTH-08). */
public record UserResponse(Long id, String email, String displayName, String role) {
}
