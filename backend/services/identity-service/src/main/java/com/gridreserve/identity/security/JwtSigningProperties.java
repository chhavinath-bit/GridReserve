package com.gridreserve.identity.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Signing settings, only in identity-service (NFR-SEC-02). The private key comes from the
 * {@code JWT_PRIVATE_KEY} environment variable; {@code keyId} says which entry of the shared
 * {@code gridreserve.jwt.public-keys} map verifies the tokens signed here.
 */
@Validated
@ConfigurationProperties("gridreserve.jwt.signing")
public record JwtSigningProperties(
        @NotBlank String keyId,
        @NotBlank String privateKey,
        @NotNull Duration accessTokenTtl) {

    // Never print the key, even if the record is logged by accident.
    @Override
    public String toString() {
        return "JwtSigningProperties[keyId=" + keyId + ", accessTokenTtl=" + accessTokenTtl + "]";
    }
}
