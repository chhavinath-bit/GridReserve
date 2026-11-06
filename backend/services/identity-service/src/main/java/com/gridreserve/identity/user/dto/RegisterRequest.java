package com.gridreserve.identity.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/register} (FR-AUTH-01..03). */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // 72 is BCrypt's input limit (in bytes; the service also checks multi-byte characters).
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String displayName) {

    // Keeps the password out of logs and debugger views.
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", displayName=" + displayName + "]";
    }
}
