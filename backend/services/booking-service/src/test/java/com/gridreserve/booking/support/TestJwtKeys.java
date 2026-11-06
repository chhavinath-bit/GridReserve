package com.gridreserve.booking.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
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
import java.util.Map;

/**
 * Throwaway RSA keys, generated when the test JVM starts, and a signer that plays identity-service.
 * Supplies the {@code gridreserve.jwt} settings the config repo provides in a real run.
 */
public class TestJwtKeys implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String KEY_ID = "test-key";
    public static final String ISSUER = "gridreserve-identity";
    public static final String AUDIENCE = "gridreserve";
    public static final KeyPair KEYS = newKeyPair();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(KEYS.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-jwt-keys", Map.of(
                "gridreserve.jwt.issuer", ISSUER,
                "gridreserve.jwt.audience", AUDIENCE,
                "gridreserve.jwt.public-keys." + KEY_ID, publicPem)));
    }

    /** A valid MEMBER token for user 42, issued now. */
    public static String memberToken() {
        return sign(KEYS, KEY_ID, claims(Instant.now()).build());
    }

    /** A valid ADMIN token for user 1, issued now. */
    public static String adminToken() {
        return sign(KEYS, KEY_ID, claims(Instant.now()).subject("1").claim("role", "ADMIN").build());
    }

    /** Claims of a normal token issued at {@code issuedAt}, valid for 15 minutes. */
    public static JwtClaimsSet.Builder claims(Instant issuedAt) {
        return JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject("42")
                .claim("role", "MEMBER")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(15 * 60));
    }

    /** Signs a token with {@code keys}; {@code kid} null means the header carries no key ID. */
    public static String sign(KeyPair keys, String kid, JwtClaimsSet claims) {
        RSAKey.Builder jwk = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate());
        JwsHeader.Builder header = JwsHeader.with(SignatureAlgorithm.RS256);
        if (kid != null) {
            jwk.keyID(kid);
            header.keyId(kid);
        }
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk.build())));
        return encoder.encode(JwtEncoderParameters.from(header.build(), claims)).getTokenValue();
    }

    public static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
