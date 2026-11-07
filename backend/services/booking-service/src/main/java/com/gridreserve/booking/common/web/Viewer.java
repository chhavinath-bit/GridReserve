package com.gridreserve.booking.common.web;

import org.springframework.security.core.Authentication;

/**
 * Who is asking. The member ID is the token's {@code sub} (the identity user ID, FR-ROLE-05), so no
 * endpoint ever takes a member ID from the request.
 */
public final class Viewer {

    private Viewer() {
    }

    /** Admins may see inactive venues and bookables (FR-CAT-06) and any booking (FR-BK-06). */
    public static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    /** The caller's member ID. Only called on endpoints that require a signed-in user. */
    public static long memberId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
