package com.gridreserve.identity.security;

import com.gridreserve.common.web.security.JwtProperties;
import com.gridreserve.common.web.security.PemKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Builds the token signer from the private key. Fails at startup if the private key does not belong
 * to the public key published under {@code keyId}: otherwise identity would happily issue tokens
 * that every other service rejects.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtSigningProperties.class)
class JwtSigningConfig {

    @Bean
    JwtEncoder jwtEncoder(JwtSigningProperties signing, JwtProperties verification) {
        String publicPem = verification.publicKeys().get(signing.keyId());
        if (publicPem == null) {
            throw new IllegalStateException("gridreserve.jwt.public-keys has no entry for signing key id '"
                    + signing.keyId() + "'");
        }
        RSAPublicKey publicKey = PemKeys.publicKey(publicPem);
        RSAPrivateKey privateKey = PemKeys.privateKey(signing.privateKey());
        if (!belongTogether(publicKey, privateKey)) {
            throw new IllegalStateException("JWT_PRIVATE_KEY does not match the public key '"
                    + signing.keyId() + "' in gridreserve.jwt.public-keys");
        }
        RSAKey key = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(signing.keyId())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    // A PKCS#8 RSA key from openssl carries the public exponent (CRT form), so the pair can be compared.
    private static boolean belongTogether(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        return publicKey.getModulus().equals(privateKey.getModulus())
                && (!(privateKey instanceof RSAPrivateCrtKey crt)
                    || publicKey.getPublicExponent().equals(crt.getPublicExponent()));
    }
}
