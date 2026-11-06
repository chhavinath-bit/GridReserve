package com.gridreserve.identity.auth.controller;

import com.gridreserve.identity.security.RefreshTokenProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.Optional;

/**
 * The refresh-token cookie (FR-AUTH-09): {@code HttpOnly} so page scripts cannot read it,
 * {@code SameSite=Strict} so other sites' requests do not carry it, and limited to
 * {@code /api/v1/auth}, so it is sent only to refresh and logout (and login/register), never to booking.
 */
@Component
class RefreshCookie {

    static final String PATH = "/api/v1/auth";

    private final RefreshTokenProperties properties;

    RefreshCookie(RefreshTokenProperties properties) {
        this.properties = properties;
    }

    /** Set on login and register, and again on every refresh so the browser's expiry slides too. */
    String set(String token) {
        return build(token, properties.ttl());
    }

    /** Tells the browser to delete the cookie (Max-Age=0). */
    String clear() {
        return build("", Duration.ZERO);
    }

    Optional<String> read(HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, properties.cookieName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank());
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
