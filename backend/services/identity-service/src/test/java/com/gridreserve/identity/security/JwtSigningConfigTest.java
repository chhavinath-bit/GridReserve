package com.gridreserve.identity.security;

import com.gridreserve.common.web.security.JwtProperties;
import com.gridreserve.identity.support.TestJwtKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSigningConfigTest {

    private static final KeyPair KEYS = TestJwtKeys.newKeyPair();
    private static final KeyPair OTHER = TestJwtKeys.newKeyPair();

    private static JwtProperties publishing(KeyPair keys) {
        return new JwtProperties("issuer", "audience",
                Map.of("key-1", TestJwtKeys.pem("PUBLIC KEY", keys.getPublic().getEncoded())));
    }

    private static JwtSigningProperties signingWith(String keyId, KeyPair keys) {
        return new JwtSigningProperties(keyId, TestJwtKeys.pem("PRIVATE KEY", keys.getPrivate().getEncoded()),
                Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("NFR-SEC-02: matching private and published public key → a signer is built")
    void matchingPair() {
        assertThat(new JwtSigningConfig().jwtEncoder(signingWith("key-1", KEYS), publishing(KEYS))).isNotNull();
    }

    @Test
    @DisplayName("NFR-SEC-02: a private key that does not match the published key stops startup, without printing the key")
    void mismatchedPairFailsFast() {
        assertThatThrownBy(() -> new JwtSigningConfig().jwtEncoder(signingWith("key-1", OTHER), publishing(KEYS)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match")
                .hasMessageNotContaining("PRIVATE KEY");
    }

    @Test
    @DisplayName("NFR-SEC-02: a signing key id that is not published stops startup")
    void unpublishedKeyIdFailsFast() {
        assertThatThrownBy(() -> new JwtSigningConfig().jwtEncoder(signingWith("key-2", KEYS), publishing(KEYS)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-2");
    }

    @Test
    @DisplayName("NFR-SEC-02: toString never includes the private key")
    void toStringHidesKey() {
        assertThat(signingWith("key-1", KEYS).toString()).doesNotContain("PRIVATE");
    }
}
