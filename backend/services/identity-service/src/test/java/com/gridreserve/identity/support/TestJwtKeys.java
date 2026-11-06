package com.gridreserve.identity.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

/**
 * A throwaway RSA key pair, generated when the test JVM starts, so no key is ever committed.
 * Supplies the JWT settings that the config repo and {@code JWT_PRIVATE_KEY} provide in a real run.
 */
public class TestJwtKeys implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String KEY_ID = "test-key";
    public static final String ISSUER = "gridreserve-identity";
    public static final String AUDIENCE = "gridreserve";
    public static final KeyPair KEYS = newKeyPair();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-jwt-keys", Map.of(
                "gridreserve.jwt.issuer", ISSUER,
                "gridreserve.jwt.audience", AUDIENCE,
                "gridreserve.jwt.public-keys." + KEY_ID, pem("PUBLIC KEY", KEYS.getPublic().getEncoded()),
                "gridreserve.jwt.signing.key-id", KEY_ID,
                "gridreserve.jwt.signing.private-key", pem("PRIVATE KEY", KEYS.getPrivate().getEncoded()),
                "gridreserve.jwt.signing.access-token-ttl", "15m")));
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

    public static String pem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }
}
