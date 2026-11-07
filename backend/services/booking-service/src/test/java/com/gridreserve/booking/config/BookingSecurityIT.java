package com.gridreserve.booking.config;

import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestJwtKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static com.gridreserve.booking.support.TestJwtKeys.KEYS;
import static com.gridreserve.booking.support.TestJwtKeys.KEY_ID;
import static com.gridreserve.booking.support.TestJwtKeys.claims;
import static com.gridreserve.booking.support.TestJwtKeys.sign;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * booking-service checks tokens and roles itself (FR-ROLE-01..03), without the gateway in front and
 * without calling identity-service. No admin endpoint exists yet (M1.5/M1.7), so an admin "allowed" there
 * shows up as 404 from Spring MVC after security let the request through, and "refused" as 401 or 403 before it.
 */
@ServiceIntegrationTest
class BookingSecurityIT {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("FS-14/FR-ROLE-02: no token → 401 UNAUTHENTICATED in the standard format")
    void missingTokenRejected_FS14() throws Exception {
        HttpResponse<String> response = get("/api/v1/categories", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"", "\"path\":\"/api/v1/categories\"");
    }

    @Test
    @DisplayName("FS-14: a token signed with another key → 401 UNAUTHENTICATED")
    void foreignKeyRejected_FS14() throws Exception {
        String forged = sign(TestJwtKeys.newKeyPair(), KEY_ID, claims(Instant.now()).build());

        HttpResponse<String> response = get("/api/v1/categories", forged);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    @DisplayName("FS-14: an expired token → 401 TOKEN_EXPIRED")
    void expiredTokenRejected_FS14() throws Exception {
        String expired = sign(KEYS, KEY_ID, claims(Instant.now().minusSeconds(3600)).build());

        HttpResponse<String> response = get("/api/v1/categories", expired);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"TOKEN_EXPIRED\"");
    }

    @Test
    @DisplayName("FR-ROLE-01: a valid member token passes security and reaches the catalog")
    void memberTokenAccepted_FRROLE01() throws Exception {
        HttpResponse<String> response = get("/api/v1/categories", TestJwtKeys.memberToken());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("FS-16/FR-ROLE-03: a MEMBER on an admin path → 403 FORBIDDEN")
    void memberOnAdminPathForbidden_FS16() throws Exception {
        HttpResponse<String> response = get("/api/v1/admin/bookings", TestJwtKeys.memberToken());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    @DisplayName("FS-16: an ADMIN on the same path passes security (404: no admin endpoint yet)")
    void adminOnAdminPathAllowed_FS16() throws Exception {
        HttpResponse<String> response = get("/api/v1/admin/bookings", TestJwtKeys.adminToken());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("NFR-SEC-08: health and the ping need no token")
    void publicPathsNeedNoToken() throws Exception {
        assertThat(get("/actuator/health", null).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/ping", null).statusCode()).isEqualTo(200);
    }
}
