package com.gridreserve.gateway;

import com.gridreserve.gateway.support.TestJwtKeys;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway's protection per route (RES-01, RES-03, RES-06) with the same filters as the config repo
 * (CircuitBreaker with a fallback, then Retry for GETs), in front of a small JDK HTTP server that can be
 * slow or failing and counts what reaches it. The response timeout is 1 s here instead of 5 s, to keep
 * the test fast.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "CONFIG_IMPORT=optional:classpath:/no-config-server.yml",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.circuitbreaker.bulkhead.resilience4j.enabled=false",
        "spring.cloud.gateway.server.webflux.httpclient.response-timeout=1s",
        "resilience4j.timelimiter.configs.default.timeout-duration=2s"
})
@ContextConfiguration(initializers = TestJwtKeys.class)
class GatewayResilienceIT {

    private static final Map<String, AtomicInteger> HITS = new ConcurrentHashMap<>();
    private static final HttpServer UPSTREAM = startUpstream();

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        route(registry, 0, "working", "/api/v1/**", "http://localhost:" + UPSTREAM.getAddress().getPort());
        // A port nothing listens on: every call fails with "connection refused".
        route(registry, 1, "down", "/down/**", "http://localhost:" + freePort());
    }

    private static void route(DynamicPropertyRegistry registry, int index, String id, String path, String uri) {
        String route = "spring.cloud.gateway.server.webflux.routes[" + index + "].";
        registry.add(route + "id", () -> id);
        registry.add(route + "uri", () -> uri);
        registry.add(route + "predicates[0].name", () -> "Path");
        registry.add(route + "predicates[0].args.pattern", () -> path);
        // Same filters, same order as gridreserve-config/api-gateway.yml.
        registry.add(route + "filters[0].name", () -> "CircuitBreaker");
        registry.add(route + "filters[0].args.name", () -> id);
        registry.add(route + "filters[0].args.fallbackUri", () -> "forward:/fallback/" + id);
        registry.add(route + "filters[1].name", () -> "Retry");
        registry.add(route + "filters[1].args.retries", () -> "2");
        registry.add(route + "filters[1].args.methods", () -> "GET");
        registry.add(route + "filters[1].args.statuses", () -> "BAD_GATEWAY,SERVICE_UNAVAILABLE");
        registry.add(route + "filters[1].args.backoff.firstBackoff", () -> "10ms");
        registry.add(route + "filters[1].args.backoff.maxBackoff", () -> "50ms");
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @Value("${local.server.port}")
    int port;

    WebTestClient client;
    String token;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(15)).build();
        token = TestJwtKeys.memberToken();
        HITS.clear();
    }

    private static int hits(String path) {
        return HITS.getOrDefault(path, new AtomicInteger()).get();
    }

    @Test
    @DisplayName("RES-01: a service slower than the route timeout → 504 UPSTREAM_TIMEOUT, standard format, the client's path")
    void slowServiceGives504() {
        client.post().uri("/api/v1/slow").header("Authorization", "Bearer " + token).exchange()
                .expectStatus().isEqualTo(504)
                .expectBody()
                .jsonPath("$.code").isEqualTo("UPSTREAM_TIMEOUT")
                .jsonPath("$.path").isEqualTo("/api/v1/slow")
                .jsonPath("$.requestId").exists();
    }

    @Test
    @DisplayName("RES-03: a GET answered 503 is retried (3 attempts in all)")
    void getIsRetried() {
        client.get().uri("/api/v1/flaky").header("Authorization", "Bearer " + token).exchange()
                .expectStatus().isEqualTo(503);
        assertThat(hits("GET /api/v1/flaky")).isEqualTo(3);
    }

    @Test
    @DisplayName("RES-03: a POST is never retried by the gateway, even after a 503 (it could create a second booking)")
    void postIsNotRetried() {
        client.post().uri("/api/v1/flaky").header("Authorization", "Bearer " + token).exchange()
                .expectStatus().isEqualTo(503);
        assertThat(hits("POST /api/v1/flaky")).isEqualTo(1);
    }

    @Test
    @DisplayName("RES-06: an unreachable service → 503 SERVICE_UNAVAILABLE in the standard format, with the client's path")
    void unreachableServiceGives503() {
        client.get().uri("/down/anything").header("Authorization", "Bearer " + token).exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.path").isEqualTo("/down/anything");
    }

    /** /api/v1/slow answers after 3 s; /api/v1/flaky always answers 503. Every request is counted. */
    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
            server.createContext("/", exchange -> {
                String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
                HITS.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
                int status = 200;
                if (key.endsWith("/api/v1/slow")) {
                    try {
                        Thread.sleep(3_000); // the upstream being slow is what this test is about
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                } else if (key.endsWith("/api/v1/flaky")) {
                    status = 503;
                }
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
