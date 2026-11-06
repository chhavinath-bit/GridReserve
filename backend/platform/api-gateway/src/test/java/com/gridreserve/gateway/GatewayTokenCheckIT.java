package com.gridreserve.gateway;

import com.gridreserve.gateway.support.TestJwtKeys;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static com.gridreserve.gateway.support.TestJwtKeys.KEYS;
import static com.gridreserve.gateway.support.TestJwtKeys.KEY_ID;
import static com.gridreserve.gateway.support.TestJwtKeys.claims;
import static com.gridreserve.gateway.support.TestJwtKeys.sign;

/**
 * The gateway's token pre-check (FR-ROLE-01, FS-14), through a real gateway. Behind it, instead of
 * a service, a tiny JDK HTTP server answers 200 and echoes whether an Authorization header arrived,
 * so a 200 proves the request got through and a 401 proves the gateway stopped it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "CONFIG_IMPORT=optional:classpath:/no-config-server.yml",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
@ContextConfiguration(initializers = TestJwtKeys.class)
class GatewayTokenCheckIT {

    private static final HttpServer UPSTREAM = startUpstream();

    @DynamicPropertySource
    static void route(DynamicPropertyRegistry registry) {
        String route = "spring.cloud.gateway.server.webflux.routes[0].";
        registry.add(route + "id", () -> "stub");
        registry.add(route + "predicates[0].name", () -> "Path");
        registry.add(route + "predicates[0].args.pattern", () -> "/api/v1/**");
        registry.add(route + "uri",
                () -> "http://localhost:" + UPSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @Value("${local.server.port}")
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("FR-ROLE-01: a valid token passes the gateway and is forwarded unchanged")
    void validTokenIsForwarded_FRROLE01() {
        client.get().uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeys.memberToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("authorization=present");
    }

    @Test
    @DisplayName("FS-14: no token → 401 UNAUTHENTICATED from the gateway, in the standard format")
    void missingTokenRejected_FS14() {
        client.get().uri("/api/v1/categories").header("X-Request-Id", "gw-401")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectHeader().valueEquals("X-Request-Id", "gw-401")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.path").isEqualTo("/api/v1/categories")
                .jsonPath("$.requestId").isEqualTo("gw-401");
    }

    @Test
    @DisplayName("FS-14: garbage, tampered, wrong-key, unknown-kid and no-kid tokens → 401 UNAUTHENTICATED")
    void badTokensRejected_FS14() {
        String valid = TestJwtKeys.memberToken();
        // Change one character in the middle of the payload: the signature no longer matches.
        int middle = valid.indexOf('.') + 10;
        char swapped = valid.charAt(middle) == 'A' ? 'B' : 'A';
        String tampered = valid.substring(0, middle) + swapped + valid.substring(middle + 1);

        String[] badTokens = {
                "not-a-jwt",
                tampered,
                sign(TestJwtKeys.newKeyPair(), KEY_ID, claims(Instant.now()).build()),
                sign(KEYS, "unknown-kid", claims(Instant.now()).build()),
                sign(KEYS, null, claims(Instant.now()).build())
        };
        for (String token : badTokens) {
            client.get().uri("/api/v1/categories")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
        }
    }

    @Test
    @DisplayName("FS-14/FR-UI-07: an expired token → 401 TOKEN_EXPIRED, the client's signal to refresh")
    void expiredTokenGetsOwnCode_FS14() {
        String expired = sign(KEYS, KEY_ID, claims(Instant.now().minusSeconds(3600)).build());

        client.get().uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    @DisplayName("FR-AUTH-01/09: the auth endpoints and the ping pass without a token")
    void publicPathsNeedNoToken() {
        client.post().uri("/api/v1/auth/login").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("authorization=absent");
        client.post().uri("/api/v1/auth/refresh").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/ping").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("FR-ROLE-01: /api/v1/me is not public; it needs a token at the gateway")
    void meNeedsToken() {
        client.get().uri("/api/v1/me").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("NFR-SEC-08: the gateway's health is public")
    void healthIsPublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                boolean hasAuthorization = exchange.getRequestHeaders().containsKey(HttpHeaders.AUTHORIZATION);
                byte[] body = ("authorization=" + (hasAuthorization ? "present" : "absent"))
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
