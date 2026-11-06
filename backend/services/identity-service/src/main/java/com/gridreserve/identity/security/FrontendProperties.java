package com.gridreserve.identity.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where the frontend is served from, e.g. {@code http://localhost:5173} (shared config, also used
 * for CORS at the gateway). Refresh and logout accept only requests whose {@code Origin} is this.
 */
@Validated
@ConfigurationProperties("gridreserve.web")
public record FrontendProperties(@NotBlank String frontendOrigin) {
}
