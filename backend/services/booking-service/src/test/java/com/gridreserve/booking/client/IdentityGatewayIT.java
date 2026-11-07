package com.gridreserve.booking.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.gridreserve.booking.client.dto.MemberDirectory;
import com.gridreserve.booking.concurrency.Parallel;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * booking-service → identity-service through {@link IdentityGateway}, against a WireMock stand-in for
 * identity (D-28) reached through the load balancer, with the A-25 resilience settings
 * (test/resources/no-config-server.yml). Each test proves one required behavior (REQUIREMENTS §13, FS-35, FS-36).
 */
@ServiceIntegrationTest
class IdentityGatewayIT {

    private static final WireMockServer identity = new WireMockServer(options().dynamicPort());

    static {
        identity.start();
    }

    @DynamicPropertySource
    static void identityAddress(DynamicPropertyRegistry registry) {
        // No Eureka in tests: the load balancer's simple discovery points identity-service at WireMock.
        registry.add("spring.cloud.discovery.client.simple.instances.identity-service[0].uri", identity::baseUrl);
    }

    @AfterAll
    static void stop() {
        identity.stop();
    }

    @Autowired
    IdentityGateway gateway;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @Autowired
    io.github.resilience4j.bulkhead.BulkheadRegistry bulkheads;

    @Autowired
    ServiceTokens tokens;

    @Autowired
    io.micrometer.core.instrument.MeterRegistry meters;

    @Autowired
    org.springframework.cloud.context.refresh.ContextRefresher refresher;

    @Autowired
    org.springframework.core.env.ConfigurableEnvironment environment;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    private static final String USERS = "/internal/users";
    private static final String TOKENS = "/internal/service-tokens";
    // "booking-service:test-client-secret" in Base64, as in test/resources/no-config-server.yml.
    private static final String BASIC = "Basic Ym9va2luZy1zZXJ2aWNlOnRlc3QtY2xpZW50LXNlY3JldA==";

    @BeforeEach
    void reset() {
        identity.resetAll();
        circuitBreakers.circuitBreaker(IdentityGateway.POLICY).reset();
        identity.stubFor(post(TOKENS).withHeader("Authorization", equalTo(BASIC))
                .willReturn(okJson("{\"accessToken\":\"service-token-1\",\"tokenType\":\"Bearer\",\"expiresIn\":300}")));
    }

    private CircuitBreaker circuit() {
        return circuitBreakers.circuitBreaker(IdentityGateway.POLICY);
    }

    private int userCalls() {
        return identity.findAll(getRequestedFor(urlPathEqualTo(USERS))).size();
    }

    @Test
    @DisplayName("D-19: names come back; the call carries a service token fetched once with the client credentials")
    void membersWithServiceToken() {
        identity.stubFor(get(urlPathEqualTo(USERS)).willReturn(okJson("""
                [{"id":1,"email":"alice@gridreserve.test","displayName":"Alice","role":"MEMBER"},
                 {"id":2,"email":"bob@gridreserve.test","displayName":"Bob"}]""")));

        MemberDirectory first = gateway.members(List.of(1L, 2L));
        gateway.members(List.of(1L));

        assertThat(first.available()).isTrue();
        assertThat(first.find(1).orElseThrow().displayName()).isEqualTo("Alice");
        assertThat(first.find(3)).isEmpty();
        identity.verify(getRequestedFor(urlPathEqualTo(USERS)).withHeader("Authorization", equalTo("Bearer service-token-1")));
        // The token is cached, not fetched per call (it may already be cached from an earlier test).
        assertThat(identity.findAll(postRequestedFor(urlPathEqualTo(TOKENS))).size()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("RES-06 / FS-33: identity down → fallback with member IDs only (memberDetailsAvailable = false)")
    void fallbackWhenDown() {
        identity.stubFor(get(urlPathEqualTo(USERS)).willReturn(aResponse().withStatus(500)));

        MemberDirectory directory = gateway.members(Set.of(7L));

        assertThat(directory.available()).isFalse();
        assertThat(directory.members()).isEmpty();
    }

    @Test
    @DisplayName("RES-01: a reply slower than the 2 s read timeout → fallback (after 3 attempts)")
    void timeoutFallsBack() {
        identity.stubFor(get(urlPathEqualTo(USERS)).willReturn(okJson("[]").withFixedDelay(2_500)));

        long began = System.nanoTime();
        MemberDirectory directory = gateway.members(List.of(1L));

        assertThat(directory.available()).isFalse();
        assertThat(userCalls()).isEqualTo(3);
        assertThat((System.nanoTime() - began) / 1_000_000).as("ms: 3 timeouts plus backoff").isLessThan(9_000);
    }

    @Test
    @DisplayName("RES-03: a 503 is retried, and the second try's answer is used")
    void retryOn503ThenSuccess() {
        identity.stubFor(get(urlPathEqualTo(USERS)).inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
        identity.stubFor(get(urlPathEqualTo(USERS)).inScenario("flaky").whenScenarioStateIs("recovered")
                .willReturn(okJson("[{\"id\":1,\"email\":\"alice@gridreserve.test\",\"displayName\":\"Alice\"}]")));

        MemberDirectory directory = gateway.members(List.of(1L));

        assertThat(directory.available()).isTrue();
        assertThat(userCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("RES-03: a 4xx is never retried and doesn't count against the circuit")
    void noRetryOn4xx() {
        identity.stubFor(get(urlPathEqualTo(USERS)).willReturn(aResponse().withStatus(400)));

        assertThat(gateway.members(List.of(1L)).available()).isFalse();
        assertThat(userCalls()).isEqualTo(1);
        assertThat(circuit().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    @DisplayName("RES-02 / FS-35: repeated 5xx open the circuit; then calls fail fast without reaching identity")
    void repeatedFailuresOpenTheCircuit() {
        identity.stubFor(get(urlPathEqualTo(USERS)).willReturn(aResponse().withStatus(500)));

        for (int call = 0; call < 4; call++) {
            gateway.members(List.of(1L)); // 3 attempts each: 12 failed calls, more than the minimum of 10
        }
        assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int reachedIdentity = userCalls();

        MemberDirectory whileOpen = gateway.members(List.of(1L));
        assertThat(whileOpen.available()).isFalse();
        assertThat(userCalls()).as("no call reaches identity while the circuit is open").isEqualTo(reachedIdentity);
    }

    @Test
    @DisplayName("RES-02: half-open lets 3 trial calls through; if they succeed, the circuit closes")
    void halfOpenRecovers() {
        circuit().transitionToOpenState();
        // Stands in for the 30 s wait: the breaker itself moves to half-open after wait-duration-in-open-state.
        circuit().transitionToHalfOpenState();
        identity.stubFor(get(urlPathEqualTo(USERS)).willReturn(okJson("[]")));

        for (int trial = 0; trial < 3; trial++) {
            assertThat(gateway.members(List.of(1L)).available()).isTrue();
        }
        assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("RES-04 / FS-36: 11 parallel calls → the 11th gets the fallback at once (bulkhead of 10), not retried")
    void bulkheadRejectsTheEleventh() throws Exception {
        identity.stubFor(get(urlPathEqualTo(USERS)).willReturn(okJson("[]").withFixedDelay(1_000)));
        tokens.current(); // fetch the token first, so all 11 calls go straight to /internal/users

        List<Boolean> available = Parallel.run(11, index -> () -> gateway.members(List.of(1L)).available());

        assertThat(available.stream().filter(ok -> ok).count()).isEqualTo(10);
        assertThat(available.stream().filter(ok -> !ok).count()).isEqualTo(1);
        assertThat(userCalls()).as("the rejected call was not retried").isEqualTo(10);
        assertThat(circuit().getMetrics().getNumberOfFailedCalls()).as("a full bulkhead is not identity's fault").isZero();
    }

    @Test
    @DisplayName("D-19: if identity refuses our client credentials (401), the call falls back and nothing is retried")
    void refusedCredentials() {
        identity.resetAll();
        identity.stubFor(post(TOKENS).willReturn(aResponse().withStatus(401)));
        tokens.forget(); // no cached token, so the next call has to ask identity for one

        assertThat(gateway.ping().instance()).isEqualTo("unavailable (fallback)");
        assertThat(identity.findAll(postRequestedFor(urlPathEqualTo(TOKENS))).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("RES-08: circuit-breaker state, retries and bulkhead are Micrometer metrics")
    void resilienceMetrics() {
        identity.stubFor(get(urlPathEqualTo(USERS)).willReturn(okJson("[]")));
        gateway.members(List.of(1L));

        assertThat(meters.find("resilience4j.circuitbreaker.state").tag("name", IdentityGateway.POLICY)
                .tag("state", "closed").gauge().value()).isEqualTo(1);
        assertThat(meters.find("resilience4j.retry.calls").tag("name", IdentityGateway.POLICY).meters()).isNotEmpty();
        assertThat(meters.find("resilience4j.bulkhead.available.concurrent.calls").tag("name", IdentityGateway.POLICY)
                .gauges()).isNotEmpty();
    }

    @Test
    @DisplayName("RES-08 / RES-07: health shows the circuit to admins only, and an open circuit doesn't make booking unhealthy")
    void healthShowsCircuitToAdminsOnly() {
        circuit().transitionToOpenState();
        com.gridreserve.booking.support.Http http = new com.gridreserve.booking.support.Http(port);

        com.gridreserve.booking.support.Http.Response asAdmin = http.get("/actuator/health",
                com.gridreserve.booking.support.TestJwtKeys.adminToken());
        assertThat(asAdmin.status()).isEqualTo(200);
        assertThat(asAdmin.body().get("status").asString()).isEqualTo("UP");
        assertThat(asAdmin.body().get("components").get("circuitBreakers").get("details")
                .get(IdentityGateway.POLICY).get("state").asString()).isEqualTo("OPEN");

        com.gridreserve.booking.support.Http.Response anonymous = http.get("/actuator/health", null);
        assertThat(anonymous.status()).isEqualTo(200);
        assertThat(anonymous.body().has("components")).isFalse();
    }

    @Test
    @DisplayName("PLAT-05: a configuration refresh rebuilds the identity-service policies with the new values")
    void refreshRebuildsPolicies_PLAT05() {
        assertThat(circuit().getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50);
        // What a changed config repo plus POST /actuator/refresh does, inside the test.
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("changed-config", java.util.Map.of(
                "resilience4j.circuitbreaker.instances.identity-service.failure-rate-threshold", "70",
                "resilience4j.bulkhead.instances.identity-service.max-concurrent-calls", "4")));
        try {
            refresher.refresh();

            assertThat(circuit().getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(70);
            assertThat(bulkheads.bulkhead(IdentityGateway.POLICY).getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(4);
        } finally {
            environment.getPropertySources().remove("changed-config");
            refresher.refresh();
        }
        assertThat(circuit().getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50);
    }
}
