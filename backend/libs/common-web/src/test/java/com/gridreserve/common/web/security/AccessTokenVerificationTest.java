package com.gridreserve.common.web.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenVerificationTest {

    private static final Instant ISSUED = Instant.parse("2026-11-06T10:00:00Z");
    private static final KeyPair KEYS = TestTokens.newKeyPair();
    private static final KeyPair OTHER_KEYS = TestTokens.newKeyPair();

    private final JwtDecoder decoder = decoderAt(ISSUED.plusSeconds(60));

    private static JwtDecoder decoderAt(Instant now) {
        JwtProperties properties = new JwtProperties(TestTokens.ISSUER, TestTokens.AUDIENCE,
                Map.of("key-1", TestTokens.publicPem(KEYS)));
        return AccessTokenVerification.decoder(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static String token(JwtClaimsSet.Builder claims) {
        return TestTokens.sign(KEYS, "key-1", claims.build());
    }

    @Test
    @DisplayName("FR-AUTH-06: a token signed with a known key is accepted; the role becomes a Spring authority")
    void validTokenAccepted_FRAUTH06() {
        Jwt jwt = decoder.decode(token(TestTokens.memberClaims(ISSUED)));

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(AccessTokenVerification.authenticationConverter().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                // Spring Security 7 also adds FACTOR_BEARER (how the user authenticated); only roles matter here.
                .filteredOn(authority -> authority.startsWith("ROLE_"))
                .containsExactly("ROLE_MEMBER");
    }

    @Test
    @DisplayName("FS-14: an expired token is rejected with the token_expired code (client refreshes)")
    void expiredTokenRejected_FS14() {
        JwtDecoder later = decoderAt(ISSUED.plusSeconds(15 * 60 + 31));

        assertThatThrownBy(() -> later.decode(token(TestTokens.memberClaims(ISSUED))))
                .isInstanceOfSatisfying(JwtValidationException.class, ex -> assertThat(ex.getErrors())
                        .anySatisfy(error -> assertThat(error.getErrorCode())
                                .isEqualTo(AccessTokenVerification.TOKEN_EXPIRED)));
    }

    @Test
    @DisplayName("FR-AUTH-06: up to 30 s of clock skew after expiry is tolerated")
    void smallSkewTolerated() {
        JwtDecoder justAfter = decoderAt(ISSUED.plusSeconds(15 * 60 + 29));

        assertThat(justAfter.decode(token(TestTokens.memberClaims(ISSUED))).getSubject()).isEqualTo("42");
    }

    @Test
    @DisplayName("FS-14: a token whose payload was changed after signing is rejected")
    void tamperedTokenRejected_FS14() {
        String member = token(TestTokens.memberClaims(ISSUED));
        String admin = token(TestTokens.memberClaims(ISSUED).claim(AccessTokenVerification.ROLE_CLAIM, "ADMIN"));
        String[] parts = member.split("\\.");
        String forged = parts[0] + "." + admin.split("\\.")[1] + "." + parts[2];

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(BadJwtException.class);
    }

    @Test
    @DisplayName("FS-14: a token signed with another key under a known kid is rejected")
    void wrongKeyRejected_FS14() {
        String token = TestTokens.sign(OTHER_KEYS, "key-1", TestTokens.memberClaims(ISSUED).build());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    @DisplayName("FR-AUTH-06: an unknown kid, or no kid at all, is rejected")
    void unknownOrMissingKidRejected_FRAUTH06() {
        String unknownKid = TestTokens.sign(KEYS, "key-9", TestTokens.memberClaims(ISSUED).build());
        String noKid = TestTokens.sign(KEYS, null, TestTokens.memberClaims(ISSUED).build());

        assertThatThrownBy(() -> decoder.decode(unknownKid)).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(noKid)).isInstanceOf(BadJwtException.class);
    }

    @Test
    @DisplayName("FR-AUTH-06: a wrong issuer or audience is rejected")
    void wrongIssuerOrAudienceRejected_FRAUTH06() {
        String otherIssuer = token(TestTokens.memberClaims(ISSUED).issuer("someone-else"));
        String otherAudience = token(TestTokens.memberClaims(ISSUED).audience(List.of("another-app")));

        assertThatThrownBy(() -> decoder.decode(otherIssuer)).isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(otherAudience)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("FR-AUTH-06: a token without a role claim is rejected")
    void missingRoleRejected_FRAUTH06() {
        JwtClaimsSet.Builder claims = TestTokens.memberClaims(ISSUED)
                .claims(all -> all.remove(AccessTokenVerification.ROLE_CLAIM));

        assertThatThrownBy(() -> decoder.decode(token(claims))).isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("PEM keys are read with real line breaks or with literal \\n from a one-line .env value")
    void pemAcceptsEscapedNewlines() {
        String oneLine = TestTokens.publicPem(KEYS).replace("\n", "\\n");

        assertThat(PemKeys.publicKey(oneLine)).isEqualTo(KEYS.getPublic());
        assertThat(PemKeys.privateKey(TestTokens.privatePem(KEYS).replace("\n", "\\n")))
                .isEqualTo(KEYS.getPrivate());
    }
}
