package com.gridreserve.identity.internal.service;

import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.DomainException;
import com.gridreserve.common.web.security.AccessTokenVerification;
import com.gridreserve.common.web.security.JwtProperties;
import com.gridreserve.identity.auth.dto.TokenResponse;
import com.gridreserve.identity.security.JwtSigningProperties;
import com.gridreserve.identity.security.ServiceClientProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Service tokens (D-19, client-credentials style): a service proves who it is with its client ID and
 * secret and gets a short-lived JWT with role {@code SERVICE}, signed with the same key as user tokens, so
 * every service verifies it the same way. {@code sub} is the client ID, never a user ID. Services accept
 * role SERVICE only on {@code /internal/**} and actuator paths; user APIs require MEMBER or ADMIN.
 */
@Service
public class ServiceTokenIssuer {

    public static final String SERVICE_ROLE = "SERVICE";

    private final ServiceClientProperties clients;
    private final JwtEncoder encoder;
    private final JwtProperties verification;
    private final JwtSigningProperties signing;
    private final Clock clock;

    public ServiceTokenIssuer(ServiceClientProperties clients, JwtEncoder encoder, JwtProperties verification,
                              JwtSigningProperties signing, Clock clock) {
        this.clients = clients;
        this.encoder = encoder;
        this.verification = verification;
        this.signing = signing;
        this.clock = clock;
    }

    public TokenResponse issue(String clientId, String secret) {
        ServiceClientProperties.Client client = clients.clients().get(clientId);
        // Compare hashes in constant time, and refuse unknown IDs with the same answer (no hint which part was wrong).
        if (client == null || secret == null || !MessageDigest.isEqual(sha256(secret),
                HexFormat.of().parseHex(client.secretSha256()))) {
            throw new DomainException(CommonErrorCode.UNAUTHENTICATED, "Unknown service client.");
        }
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(verification.issuer())
                .audience(List.of(verification.audience()))
                .subject(clientId)
                .claim(AccessTokenVerification.ROLE_CLAIM, SERVICE_ROLE)
                .issuedAt(now)
                .expiresAt(now.plus(clients.tokenTtl()))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(signing.keyId()).build();
        return TokenResponse.bearer(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
                clients.tokenTtl().toSeconds());
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
