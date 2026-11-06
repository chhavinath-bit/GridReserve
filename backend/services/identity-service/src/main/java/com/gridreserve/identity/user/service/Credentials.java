package com.gridreserve.identity.user.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Small input rules shared by registration and login. */
public final class Credentials {

    /** BCrypt reads at most 72 bytes; longer input is rejected rather than silently cut. */
    static final int BCRYPT_MAX_BYTES = 72;

    private Credentials() {
    }

    /** Emails are compared and stored lower-cased (FR-AUTH-02). */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** 72 characters can be more than 72 bytes when they include accents or emoji. */
    public static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }
}
