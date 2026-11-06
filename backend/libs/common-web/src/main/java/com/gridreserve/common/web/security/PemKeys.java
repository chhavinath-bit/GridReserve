package com.gridreserve.common.web.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Reads RSA keys from PEM text as it arrives from configuration or an environment variable.
 * Accepts real line breaks or literal {@code \n} (a PEM squeezed into one {@code .env} line).
 * Error messages never include the key itself.
 */
public final class PemKeys {

    private PemKeys() {
    }

    /** {@code -----BEGIN PUBLIC KEY-----} (X.509 SubjectPublicKeyInfo), as printed by {@code openssl pkey -pubout}. */
    public static RSAPublicKey publicKey(String pem) {
        try {
            byte[] der = decode(pem, "PUBLIC KEY");
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException ex) {
            throw new IllegalArgumentException("Not a valid RSA public key in PEM format", ex);
        }
    }

    /** {@code -----BEGIN PRIVATE KEY-----} (PKCS#8), as printed by {@code openssl genpkey}. */
    public static RSAPrivateKey privateKey(String pem) {
        try {
            byte[] der = decode(pem, "PRIVATE KEY");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException ex) {
            // No cause: some providers echo key material in their messages.
            throw new IllegalArgumentException("Not a valid RSA private key in PKCS#8 PEM format");
        }
    }

    private static byte[] decode(String pem, String label) {
        if (pem == null) {
            throw new IllegalArgumentException("missing");
        }
        String body = pem.replace("\\n", "\n")
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
