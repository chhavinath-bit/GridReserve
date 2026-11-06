package com.gridreserve.identity.auth.service;

import com.gridreserve.common.web.security.AccessTokenVerification;
import com.gridreserve.common.web.security.JwtProperties;
import com.gridreserve.identity.auth.dto.TokenResponse;
import com.gridreserve.identity.security.JwtSigningProperties;
import com.gridreserve.identity.user.entity.AppUser;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Signs access tokens (FR-AUTH-06): RS256, {@code kid} in the header, and only the claims other
 * services need ({@code sub} = user ID, {@code role}). No email or name: a token is readable by
 * anyone who holds it.
 */
@Component
public class AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties verification;
    private final JwtSigningProperties signing;
    private final Clock clock;

    public AccessTokenIssuer(JwtEncoder encoder, JwtProperties verification, JwtSigningProperties signing,
                             Clock clock) {
        this.encoder = encoder;
        this.verification = verification;
        this.signing = signing;
        this.clock = clock;
    }

    public TokenResponse issue(AppUser user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(verification.issuer())
                .audience(List.of(verification.audience()))
                .subject(user.getId().toString())
                .claim(AccessTokenVerification.ROLE_CLAIM, user.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plus(signing.accessTokenTtl()))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(signing.keyId()).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return TokenResponse.bearer(token, signing.accessTokenTtl().toSeconds());
    }
}
