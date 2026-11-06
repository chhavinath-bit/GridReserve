package com.gridreserve.identity.refresh;

import com.gridreserve.identity.refresh.service.RefreshTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokensTest {

    @Test
    @DisplayName("FR-AUTH-09: a token is 256 random bits, URL-safe, and never repeats")
    void tokenIs256RandomBits_FRAUTH09() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String token = RefreshTokens.newToken();
            assertThat(token).matches("[A-Za-z0-9_-]{43}");
            assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
            seen.add(token);
        }
        assertThat(seen).hasSize(1_000);
    }

    @Test
    @DisplayName("FR-AUTH-09: the stored hash is SHA-256 in hex, the same for the same token and different otherwise")
    void hashIsStableSha256_FRAUTH09() {
        // Known SHA-256 test vector, so this checks the algorithm, not just "some 64 characters".
        assertThat(RefreshTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        String token = RefreshTokens.newToken();
        assertThat(RefreshTokens.hash(token)).isEqualTo(RefreshTokens.hash(token)).doesNotContain(token);
        assertThat(RefreshTokens.hash(token)).isNotEqualTo(RefreshTokens.hash(RefreshTokens.newToken()));
    }
}
