package com.gridreserve.common.web.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * What every service needs to verify access tokens (FR-AUTH-06), from the shared config-repo file.
 * Public keys are not secret; the map is keyed by {@code kid} so a new key can be added before the
 * old one is retired (manual rotation, A-27). Missing values stop the service at startup.
 */
@Validated
@ConfigurationProperties("gridreserve.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotEmpty Map<String, String> publicKeys) {
}
