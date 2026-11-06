package com.gridreserve.booking.common.web;

import org.springframework.security.core.Authentication;

/** Who is asking, as far as read endpoints care: admins may see inactive venues and bookables (FR-CAT-06). */
public final class Viewer {

    private Viewer() {
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
