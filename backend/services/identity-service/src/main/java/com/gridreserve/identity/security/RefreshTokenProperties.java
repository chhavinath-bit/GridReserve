package com.gridreserve.identity.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Refresh-token cookie settings (FR-AUTH-09). {@code cookieSecure} is false only in dev, where the
 * app runs on plain http://localhost; prod sets it to true.
 */
@Validated
@ConfigurationProperties("gridreserve.auth.refresh")
public record RefreshTokenProperties(
        @NotNull Duration ttl,
        @NotBlank String cookieName,
        boolean cookieSecure) {
}
