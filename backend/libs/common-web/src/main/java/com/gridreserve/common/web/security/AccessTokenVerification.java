package com.gridreserve.common.web.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * How every servlet service checks an access token locally, with the public keys only
 * (FR-AUTH-06: no call to identity-service). A token is accepted when all of these hold:
 * <ol>
 *   <li>its header names a {@code kid} we know, and the RS256 signature matches that key;</li>
 *   <li>it has not expired (by the injected {@link Clock}, with a small skew allowance);</li>
 *   <li>{@code iss} and {@code aud} are ours, and {@code sub} and {@code role} are present.</li>
 * </ol>
 */
public final class AccessTokenVerification {

    /** Claim holding the user's role ({@code MEMBER} or {@code ADMIN}). */
    public static final String ROLE_CLAIM = "role";

    /** Validation error code for an expired token, turned into {@code 401 TOKEN_EXPIRED} for the client. */
    public static final String TOKEN_EXPIRED = "token_expired";

    /** Tolerates small clock differences between the issuing and the verifying host. */
    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private AccessTokenVerification() {
    }

    public static JwtDecoder decoder(JwtProperties properties, Clock clock) {
        List<JWK> keys = properties.publicKeys().entrySet().stream()
                .map(entry -> (JWK) new RSAKey.Builder(PemKeys.publicKey(entry.getValue()))
                        .keyID(entry.getKey())
                        .keyUse(KeyUse.SIGNATURE)
                        .algorithm(JWSAlgorithm.RS256)
                        .build())
                .toList();
        JWSKeySelector<SecurityContext> byKid =
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(new JWKSet(keys)));

        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        // Without a kid, Nimbus would try every key; we require the token to say which key signed it.
        processor.setJWSKeySelector((header, context) ->
                header.getKeyID() == null ? List.of() : byKid.selectJWSKeys(header, context));
        // Claims are checked by the Spring validators below, so expiry follows our Clock, not the system time.
        processor.setJWTClaimsSetVerifier((claims, context) -> {
        });

        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new ExpiryValidator(clock),
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(properties.audience())),
                new JwtClaimValidator<Object>(JwtClaimNames.SUB, Objects::nonNull),
                new JwtClaimValidator<Object>(ROLE_CLAIM, Objects::nonNull)));
        return decoder;
    }

    /** Maps the {@code role} claim to a Spring authority, e.g. {@code ADMIN} → {@code ROLE_ADMIN}. */
    public static JwtAuthenticationConverter authenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** Own expiry check, so an expired token gets its own error code (the client then refreshes). */
    private record ExpiryValidator(Clock clock) implements OAuth2TokenValidator<Jwt> {

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt == null) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The token has no expiry.", null));
            }
            if (clock.instant().minus(CLOCK_SKEW).isAfter(expiresAt)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(TOKEN_EXPIRED, "The token has expired.", null));
            }
            return OAuth2TokenValidatorResult.success();
        }
    }
}
