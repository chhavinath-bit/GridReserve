package com.gridreserve.common.web.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** Throwaway RSA keys and a signer for tests. Nothing here is a real key. */
final class TestTokens {

    static final String ISSUER = "gridreserve-identity";
    static final String AUDIENCE = "gridreserve";

    private TestTokens() {
    }

    static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String publicPem(KeyPair keys) {
        return pem("PUBLIC KEY", keys.getPublic().getEncoded());
    }

    static String privatePem(KeyPair keys) {
        return pem("PRIVATE KEY", keys.getPrivate().getEncoded());
    }

    private static String pem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    /** Signs a token; {@code kid} null means the header carries no key ID. */
    static String sign(KeyPair keys, String kid, JwtClaimsSet claims) {
        RSAKey.Builder jwk = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate());
        if (kid != null) {
            jwk.keyID(kid);
        }
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk.build())));
        JwsHeader.Builder header = JwsHeader.with(SignatureAlgorithm.RS256);
        if (kid != null) {
            header.keyId(kid);
        }
        return encoder.encode(JwtEncoderParameters.from(header.build(), claims)).getTokenValue();
    }

    /** Claims of a normal member token issued at {@code issuedAt}, valid for 15 minutes. */
    static JwtClaimsSet.Builder memberClaims(Instant issuedAt) {
        return JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject("42")
                .claim(AccessTokenVerification.ROLE_CLAIM, "MEMBER")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(15 * 60));
    }
}
