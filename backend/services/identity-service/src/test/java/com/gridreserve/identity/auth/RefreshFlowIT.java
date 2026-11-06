package com.gridreserve.identity.auth;

import com.gridreserve.identity.refresh.service.RefreshTokens;
import com.gridreserve.identity.support.Http;
import com.gridreserve.identity.support.Http.Response;
import com.gridreserve.identity.support.ServiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** The refresh-token cookie over real HTTP against PostgreSQL (FR-AUTH-09..13, D-37). */
@ServiceIntegrationTest
class RefreshFlowIT {

    private static final String COOKIE = "gr_refresh";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    Http http;

    @BeforeEach
    void setUp() {
        http = new Http(port);
    }

    /** Registers a fresh member and returns the refresh token from its cookie. */
    private String registerAndGetRefreshToken() {
        Response registered = http.post("/api/v1/auth/register", """
                {"email":"user-%s@example.test","password":"secret-pass","displayName":"Test User"}"""
                .formatted(UUID.randomUUID()));
        assertThat(registered.status()).isEqualTo(201);
        return registered.cookieValue(COOKIE);
    }

    /** Headers a real browser on our frontend sends with refresh and logout. */
    private static Map<String, String> fromOurApp(String refreshToken) {
        return Map.of("Cookie", COOKIE + "=" + refreshToken,
                "Origin", ServiceIntegrationTest.FRONTEND_ORIGIN,
                "X-GridReserve-Client", "web");
    }

    private Response refresh(String refreshToken) {
        return http.post(REFRESH, "", fromOurApp(refreshToken));
    }

    private Instant expiresAt(String refreshToken) {
        return jdbc.queryForObject("SELECT expires_at FROM refresh_token WHERE token_hash = ?",
                Timestamp.class, RefreshTokens.hash(refreshToken)).toInstant();
    }

    private void setExpiresAt(String refreshToken, Instant expiresAt) {
        jdbc.update("UPDATE refresh_token SET expires_at = ? WHERE token_hash = ?",
                Timestamp.from(expiresAt), RefreshTokens.hash(refreshToken));
    }

    @Nested
    @DisplayName("Starting a session")
    class StartingASession {

        @Test
        @DisplayName("FR-AUTH-05/09: login sets an HttpOnly, SameSite=Strict cookie for /api/v1/auth that lasts 7 days")
        void loginSetsCookie_FRAUTH09() {
            Response login = http.post("/api/v1/auth/login", """
                    {"email":"bob@gridreserve.test","password":"demo-bob-pass"}""");

            assertThat(login.status()).isEqualTo(200);
            assertThat(login.setCookie(COOKIE)).hasValueSatisfying(cookie -> assertThat(cookie)
                    .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=604800")
                    // Secure is switched on in prod only (dev and tests run on plain http).
                    .doesNotContain("Secure"));
        }

        @Test
        @DisplayName("FR-AUTH-09: only the SHA-256 of the token is stored, one row per login, expiring in 7 days")
        void onlyHashStored_FRAUTH09() {
            String token = registerAndGetRefreshToken();

            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE token_hash = ?",
                    Integer.class, RefreshTokens.hash(token))).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE token_hash = ?",
                    Integer.class, token)).isZero();
            assertThat(Duration.between(Instant.now(), expiresAt(token)))
                    .isBetween(Duration.ofDays(7).minusMinutes(1), Duration.ofDays(7));
        }

        @Test
        @DisplayName("D-37: logging in deletes expired sessions (of anyone), so the table does not grow without a job")
        void loginDeletesExpiredRows_D37() {
            String stale = registerAndGetRefreshToken();
            setExpiresAt(stale, Instant.now().minusSeconds(1));

            registerAndGetRefreshToken();

            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE token_hash = ?",
                    Integer.class, RefreshTokens.hash(stale))).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE expires_at <= now()",
                    Integer.class)).isZero();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("FR-AUTH-10: a valid cookie gives a working access token and the same cookie again with a fresh 7 days")
        void refreshGivesAccessTokenAndSlides_FRAUTH10() {
            String token = registerAndGetRefreshToken();
            setExpiresAt(token, Instant.now().plus(Duration.ofHours(1)));

            Response refreshed = refresh(token);

            assertThat(refreshed.status()).isEqualTo(200);
            assertThat(http.get("/api/v1/me", refreshed.body().get("accessToken").asString()).status()).isEqualTo(200);
            // Not rotated: the browser keeps the same token, only its expiry moves (in the DB and the cookie).
            assertThat(refreshed.cookieValue(COOKIE)).isEqualTo(token);
            assertThat(refreshed.setCookie(COOKIE)).hasValueSatisfying(cookie -> assertThat(cookie).contains("Max-Age=604800"));
            assertThat(expiresAt(token)).isAfter(Instant.now().plus(Duration.ofDays(7)).minusSeconds(60));
        }

        @Test
        @DisplayName("FR-AUTH-09/10: after 7 days without use the session has ended → 401 REFRESH_TOKEN_INVALID")
        void expiredAfterSevenIdleDays_FRAUTH10() {
            String token = registerAndGetRefreshToken();
            setExpiresAt(token, Instant.now().minusSeconds(1));

            Response refreshed = refresh(token);

            assertThat(refreshed.status()).isEqualTo(401);
            assertThat(refreshed.code()).isEqualTo("REFRESH_TOKEN_INVALID");
            assertThat(refreshed.setCookie(COOKIE)).isEmpty();
        }

        @Test
        @DisplayName("FR-AUTH-10: no cookie, or a token we never issued → 401 REFRESH_TOKEN_INVALID")
        void missingOrUnknownToken_FRAUTH10() {
            Response noCookie = http.post(REFRESH, "", Map.of(
                    "Origin", ServiceIntegrationTest.FRONTEND_ORIGIN, "X-GridReserve-Client", "web"));
            Response unknown = refresh(RefreshTokens.newToken());

            assertThat(noCookie.status()).isEqualTo(401);
            assertThat(noCookie.code()).isEqualTo("REFRESH_TOKEN_INVALID");
            assertThat(unknown.status()).isEqualTo(401);
            assertThat(unknown.code()).isEqualTo("REFRESH_TOKEN_INVALID");
        }

        @Test
        @DisplayName("FR-AUTH-13: a disabled user gets no new access token, and the session's expiry is not moved")
        void disabledUserRefused_FRAUTH13() {
            String token = registerAndGetRefreshToken();
            Instant before = expiresAt(token);
            jdbc.update("""
                    UPDATE app_user SET enabled = false
                    WHERE id = (SELECT user_id FROM refresh_token WHERE token_hash = ?)""", RefreshTokens.hash(token));

            Response refreshed = refresh(token);

            assertThat(refreshed.status()).isEqualTo(401);
            assertThat(refreshed.code()).isEqualTo("REFRESH_TOKEN_INVALID");
            assertThat(expiresAt(token)).isEqualTo(before);
        }

        @Test
        @DisplayName("FS-40: another site's Origin, no Origin, or no X-GridReserve-Client header → 403, and the session is untouched")
        void foreignCallerForbidden_FS40() {
            String token = registerAndGetRefreshToken();
            String cookie = COOKIE + "=" + token;

            List<Response> refused = List.of(
                    http.post(REFRESH, "", Map.of("Cookie", cookie, "Origin", "https://evil.example", "X-GridReserve-Client", "web")),
                    http.post(REFRESH, "", Map.of("Cookie", cookie, "X-GridReserve-Client", "web")),
                    http.post(REFRESH, "", Map.of("Cookie", cookie, "Origin", ServiceIntegrationTest.FRONTEND_ORIGIN)),
                    http.post(LOGOUT, "", Map.of("Cookie", cookie, "Origin", "https://evil.example", "X-GridReserve-Client", "web")));

            assertThat(refused).allSatisfy(response -> {
                assertThat(response.status()).isEqualTo(403);
                assertThat(response.code()).isEqualTo("FORBIDDEN");
            });
            // The forged logout did not end the session.
            assertThat(refresh(token).status()).isEqualTo(200);
        }

        @Test
        @DisplayName("FR-AUTH-10: ten parallel refreshes of one cookie (several tabs) all succeed; the session stays")
        void parallelRefreshesAllSucceed_FRAUTH10() throws Exception {
            String token = registerAndGetRefreshToken();
            int callers = 10;
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(callers);
            try {
                List<Future<Integer>> results = new ArrayList<>();
                for (int i = 0; i < callers; i++) {
                    results.add(pool.submit(() -> {
                        start.await();
                        return refresh(token).status();
                    }));
                }
                start.countDown();

                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> result : results) {
                    statuses.add(result.get());
                }
                assertThat(statuses).containsOnly(200).hasSize(callers);
            } finally {
                pool.shutdownNow();
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE token_hash = ?",
                    Integer.class, RefreshTokens.hash(token))).isEqualTo(1);
        }

        @Test
        @DisplayName("FR-AUTH-10: a stale Authorization header sent along does not block refresh or logout")
        void staleAccessTokenIgnored_FRAUTH10() {
            String token = registerAndGetRefreshToken();
            Map<String, String> withStaleHeader = new HashMap<>(fromOurApp(token));
            withStaleHeader.put("Authorization", "Bearer not-a-valid-access-token");

            assertThat(http.post(REFRESH, "", withStaleHeader).status()).isEqualTo(200);
            assertThat(http.post(LOGOUT, "", withStaleHeader).status()).isEqualTo(204);
        }

        @Test
        @DisplayName("Security: an access token sent as the refresh cookie is not a session → 401")
        void accessTokenIsNotARefreshToken() {
            Response login = http.post("/api/v1/auth/login", """
                    {"email":"bob@gridreserve.test","password":"demo-bob-pass"}""");

            Response refreshed = refresh(login.body().get("accessToken").asString());

            assertThat(refreshed.status()).isEqualTo(401);
            assertThat(refreshed.code()).isEqualTo("REFRESH_TOKEN_INVALID");
        }

        @Test
        @DisplayName("Security: a refresh token used as a Bearer token on /me → 401")
        void refreshTokenIsNotAnAccessToken() {
            String token = registerAndGetRefreshToken();

            assertThat(http.get("/api/v1/me", token).status()).isEqualTo(401);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("FR-AUTH-11: logout deletes the session and clears the cookie; the old cookie then gets 401")
        void logoutEndsSession_FRAUTH11() {
            String token = registerAndGetRefreshToken();

            Response logout = http.post(LOGOUT, "", fromOurApp(token));

            assertThat(logout.status()).isEqualTo(204);
            assertThat(logout.setCookie(COOKIE)).hasValueSatisfying(cookie ->
                    assertThat(cookie).startsWith(COOKIE + "=;").contains("Max-Age=0", "Path=/api/v1/auth"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE token_hash = ?",
                    Integer.class, RefreshTokens.hash(token))).isZero();
            // Even someone who copied the cookie before logout can no longer use it.
            assertThat(refresh(token).code()).isEqualTo("REFRESH_TOKEN_INVALID");
        }

        @Test
        @DisplayName("FR-AUTH-11: logging out on one device keeps the user's other devices logged in")
        void otherDevicesStayLoggedIn_FRAUTH11() {
            String email = "user-" + UUID.randomUUID() + "@example.test";
            String laptop = http.post("/api/v1/auth/register", """
                    {"email":"%s","password":"secret-pass","displayName":"Test User"}""".formatted(email))
                    .cookieValue(COOKIE);
            String phone = http.post("/api/v1/auth/login", """
                    {"email":"%s","password":"secret-pass"}""".formatted(email)).cookieValue(COOKIE);

            http.post(LOGOUT, "", fromOurApp(laptop));

            assertThat(refresh(laptop).status()).isEqualTo(401);
            assertThat(refresh(phone).status()).isEqualTo(200);
        }

        @Test
        @DisplayName("FR-AUTH-11: logout without a cookie, or twice, still answers 204")
        void logoutIsIdempotent_FRAUTH11() {
            String token = registerAndGetRefreshToken();
            Map<String, String> noCookie = Map.of(
                    "Origin", ServiceIntegrationTest.FRONTEND_ORIGIN, "X-GridReserve-Client", "web");

            assertThat(http.post(LOGOUT, "", fromOurApp(token)).status()).isEqualTo(204);
            assertThat(http.post(LOGOUT, "", fromOurApp(token)).status()).isEqualTo(204);
            assertThat(http.post(LOGOUT, "", noCookie).status()).isEqualTo(204);
        }
    }
}
