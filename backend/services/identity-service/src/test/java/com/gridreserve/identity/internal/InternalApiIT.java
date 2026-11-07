package com.gridreserve.identity.internal;

import com.gridreserve.identity.support.Http;
import com.gridreserve.identity.support.Http.Response;
import com.gridreserve.identity.support.ServiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Service-to-service API (D-19, EVT-05): service tokens for known clients only, /internal/users only with a
 * service token, and a service token never accepted as a user.
 */
@ServiceIntegrationTest
class InternalApiIT {

    @Value("${local.server.port}")
    int port;

    Http http;

    @BeforeEach
    void setUp() {
        http = new Http(port);
    }

    private static Map<String, String> basic(String id, String secret) {
        return Map.of("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8)));
    }

    private String serviceToken() {
        Response token = http.post("/internal/service-tokens", "", basic("booking-service", "test-client-secret"));
        assertThat(token.status()).isEqualTo(200);
        return token.body().get("accessToken").asString();
    }

    private String memberToken() {
        String email = "internal-" + System.nanoTime() + "@example.test";
        Response registered = http.post("/api/v1/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"long-enough-1\",\"displayName\":\"Internal Test\"}");
        assertThat(registered.status()).isEqualTo(201);
        return registered.body().get("accessToken").asString();
    }

    private static JsonNode claims(String jwt) {
        String payload = jwt.split("\\.")[1];
        return JsonMapper.builder().build().readTree(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("D-19: the right client secret gets a 5-minute token with role SERVICE and the client ID as sub")
    void serviceTokenForKnownClient() {
        Response token = http.post("/internal/service-tokens", "", basic("booking-service", "test-client-secret"));

        assertThat(token.status()).isEqualTo(200);
        assertThat(token.body().get("expiresIn").asLong()).isEqualTo(300);
        JsonNode claims = claims(token.body().get("accessToken").asString());
        assertThat(claims.get("role").asString()).isEqualTo("SERVICE");
        assertThat(claims.get("sub").asString()).isEqualTo("booking-service");
    }

    @Test
    @DisplayName("D-19: a wrong secret, an unknown client or no credentials → 401 UNAUTHENTICATED, same answer for all")
    void serviceTokenRefused() {
        for (Map<String, String> headers : java.util.List.of(basic("booking-service", "wrong"),
                basic("someone-else", "test-client-secret"), Map.<String, String>of())) {
            Response refused = http.post("/internal/service-tokens", "", headers);
            assertThat(refused.status()).isEqualTo(401);
            assertThat(refused.code()).isEqualTo("UNAUTHENTICATED");
            assertThat(refused.body().get("message").asString()).isEqualTo("Unknown service client.");
        }
    }

    @Test
    @DisplayName("EVT-05: /internal/users needs a service token: none → 401, a member's token → 403, a service token → 200")
    void usersOnlyForServices() {
        String member = memberToken();
        assertThat(http.get("/internal/users?ids=1", null).status()).isEqualTo(401);
        assertThat(http.get("/internal/users?ids=1", member).status()).isEqualTo(403);

        Response users = http.get("/internal/users?ids=1,999999", serviceToken());
        assertThat(users.status()).isEqualTo(200);
        assertThat(users.body().size()).isEqualTo(1); // seeded admin (id 1); unknown IDs are left out
        assertThat(users.body().get(0).get("email").asString()).isNotBlank();
        assertThat(users.body().get(0).has("passwordHash")).isFalse();
        assertThat(users.body().get(0).has("role")).isFalse();
    }

    @Test
    @DisplayName("A service token can't act as a user: /api/v1/me → 403")
    void serviceTokenIsNotAUser() {
        assertThat(http.get("/api/v1/me", serviceToken()).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("At most 100 IDs per call → 400")
    void tooManyIds() {
        String ids = String.join(",", java.util.stream.IntStream.rangeClosed(1, 101).mapToObj(String::valueOf).toList());
        assertThat(http.get("/internal/users?ids=" + ids, serviceToken()).status()).isEqualTo(400);
    }
}
