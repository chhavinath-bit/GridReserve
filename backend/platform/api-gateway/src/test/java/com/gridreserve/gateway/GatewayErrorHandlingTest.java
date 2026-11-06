package com.gridreserve.gateway;

import com.gridreserve.gateway.support.TestJwtKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Runs the gateway without Config Server or Eureka (test settings below) and checks that its
 * own responses use the standard error JSON and carry a request ID.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "CONFIG_IMPORT=optional:classpath:/no-config-server.yml",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
@ContextConfiguration(initializers = TestJwtKeys.class)
class GatewayErrorHandlingTest {

    @Value("${local.server.port}")
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("API-05/PLAT-12: an unknown path returns 404 in the standard error format with a request ID")
    void unknownPathUsesStandardFormat() {
        // With a valid token: without one, the token check (GatewayTokenCheckIT) answers 401 first.
        client.get().uri("/api/v1/does-not-exist").header("X-Request-Id", "test-1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtKeys.memberToken())
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals("X-Request-Id", "test-1")
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.requestId").isEqualTo("test-1")
                .jsonPath("$.path").isEqualTo("/api/v1/does-not-exist");
    }

    @Test
    @DisplayName("RES-06: the circuit-breaker fallback returns 503 SERVICE_UNAVAILABLE in the standard format")
    void fallbackReturns503() {
        client.get().uri("/fallback/booking-service")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.requestId").exists();
    }
}
