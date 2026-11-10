package com.gridreserve.booking.booking;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.gridreserve.booking.client.IdentityGateway;
import com.gridreserve.booking.support.Http;
import com.gridreserve.booking.support.Http.Response;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestJwtKeys;
import com.gridreserve.booking.support.TestBookings;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The admin booking list through HTTP (FR-ADM-01, US-10): filters, paging, names from identity-service in one
 * call per page, and the degraded answer when identity is down (FS-33, RES-06). Identity is a WireMock
 * stand-in (D-28) reached through the load balancer, as in IdentityGatewayIT.
 */
@ServiceIntegrationTest
class AdminBookingsIT {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final long ALICE = 970_001L;
    private static final long BOB = 970_002L;
    private static final String USERS = "/internal/users";
    private static final WireMockServer identity = new WireMockServer(options().dynamicPort());

    static {
        identity.start();
    }

    @DynamicPropertySource
    static void identityAddress(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.identity-service[0].uri", identity::baseUrl);
    }

    @AfterAll
    static void stop() {
        identity.stop();
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    Http http;
    long courtA;
    long courtB;
    long room;
    String window;

    @BeforeEach
    void setUp() {
        http = new Http(port);
        identity.resetAll();
        circuitBreakers.circuitBreaker(IdentityGateway.POLICY).reset();
        identity.stubFor(post("/internal/service-tokens")
                .willReturn(okJson("{\"accessToken\":\"service-token\",\"tokenType\":\"Bearer\",\"expiresIn\":300}")));
        identity.stubFor(get(urlPathEqualTo(USERS)).willReturn(okJson("""
                [{"id":970001,"email":"alice@example.test","displayName":"Alice Test"},
                 {"id":970002,"email":"bob@example.test","displayName":"Bob Test"}]""")));
        courtA = bookable("Court A");
        courtB = bookable("Court B");
        room = bookable("Group Room 2");
        LocalDate tomorrow = LocalDate.now(KOLKATA).plusDays(1);
        window = "&from=" + tomorrow.atStartOfDay(KOLKATA).toInstant() + "&to=" + tomorrow.plusDays(1).atStartOfDay(KOLKATA).toInstant();

        // Tomorrow: Alice on Court A at 18:00 (confirmed) and in Group Room 2 at 10:00; Bob on Court B at 19:00 (held).
        confirm(ALICE, hold(ALICE, courtA, "18:00", "19:00"));
        hold(ALICE, room, "10:00", "11:00");
        hold(BOB, courtB, "19:00", "20:00");
    }

    @AfterEach
    void cleanUp() {
        TestBookings.delete(jdbc, "member_id IN (?, ?)", ALICE, BOB);
    }

    private long bookable(String name) {
        return jdbc.queryForObject("SELECT id FROM bookable WHERE name = ?", Long.class, name);
    }

    private static Instant tomorrowAt(String localTime) {
        return LocalDate.now(KOLKATA).plusDays(1).atTime(LocalTime.parse(localTime)).atZone(KOLKATA).toInstant();
    }

    private long hold(long member, long bookableId, String from, String to) {
        Response held = http.post("/api/v1/bookings", TestJwtKeys.memberToken(member), Map.of("bookableId", bookableId,
                "start", tomorrowAt(from).toString(), "end", tomorrowAt(to).toString()));
        assertThat(held.status()).as("hold: %s", held.body()).isEqualTo(201);
        return held.body().get("id").asLong();
    }

    private void confirm(long member, long bookingId) {
        assertThat(http.post("/api/v1/bookings/" + bookingId + "/confirm", TestJwtKeys.memberToken(member), null).status())
                .isEqualTo(200);
    }

    private JsonNode list(String query) {
        Response response = http.get("/api/v1/admin/bookings?" + query + window, TestJwtKeys.adminToken());
        assertThat(response.status()).as("%s", response.body()).isEqualTo(200);
        return response.body();
    }

    private static List<String> starts(JsonNode page) {
        List<String> starts = new ArrayList<>();
        page.get("items").forEach(item -> starts.add(item.get("booking").get("start").asString()));
        return starts;
    }

    @Test
    @DisplayName("FR-ADM-01: every member's bookings, newest start first, with names from one identity call for the page")
    void allBookingsWithNames_FRADM01() {
        JsonNode page = list("");

        assertThat(page.get("totalItems").asLong()).isEqualTo(3);
        assertThat(page.get("memberDetailsAvailable").asBoolean()).isTrue();
        assertThat(starts(page)).containsExactly(tomorrowAt("19:00").toString(), tomorrowAt("18:00").toString(),
                tomorrowAt("10:00").toString());
        JsonNode bob = page.get("items").get(0);
        assertThat(bob.get("memberId").asLong()).isEqualTo(BOB);
        assertThat(bob.get("memberEmail").asString()).isEqualTo("bob@example.test");
        assertThat(bob.get("memberDisplayName").asString()).isEqualTo("Bob Test");
        assertThat(bob.get("booking").get("bookableName").asString()).isEqualTo("Court B");
        assertThat(bob.get("booking").get("status").asString()).isEqualTo("HELD");

        List<LoggedRequest> calls = identity.findAll(getRequestedFor(urlPathEqualTo(USERS)));
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().queryParameter("ids").values()).containsExactlyInAnyOrder("970001", "970002");
    }

    @Test
    @DisplayName("FR-ADM-01: filters by venue, bookable and status, alone and together")
    void filters_FRADM01() {
        long turf = jdbc.queryForObject("SELECT id FROM venue WHERE name = 'Sunrise Turf'", Long.class);

        assertThat(list("venueId=" + turf).get("totalItems").asLong()).isEqualTo(2);
        assertThat(list("bookableId=" + courtA).get("totalItems").asLong()).isEqualTo(1);
        assertThat(list("status=HELD").get("totalItems").asLong()).isEqualTo(2);
        assertThat(list("status=CONFIRMED&venueId=" + turf).get("totalItems").asLong()).isEqualTo(1);
        assertThat(list("venueId=" + turf + "&bookableId=" + room).get("totalItems").asLong())
                .as("a bookable of another venue").isZero();
        assertThat(list("venueId=99999999").get("items")).isEmpty();
    }

    @Test
    @DisplayName("API-04: pages of the admin list; size above 100 refused")
    void paging_API04() {
        JsonNode first = list("size=2&page=0");
        JsonNode second = list("size=2&page=1");

        assertThat(first.get("items")).hasSize(2);
        assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        assertThat(second.get("items")).hasSize(1);
        assertThat(http.get("/api/v1/admin/bookings?size=101", TestJwtKeys.adminToken()).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("FS-33 / RES-06: identity down → the list still loads, with member IDs only and memberDetailsAvailable = false")
    void identityDown_FS33() {
        identity.resetMappings();
        identity.stubFor(post("/internal/service-tokens")
                .willReturn(okJson("{\"accessToken\":\"service-token\",\"tokenType\":\"Bearer\",\"expiresIn\":300}")));
        identity.stubFor(get(urlPathEqualTo(USERS)).willReturn(aResponse().withStatus(503)));

        JsonNode page = list("");

        assertThat(page.get("totalItems").asLong()).isEqualTo(3);
        assertThat(page.get("memberDetailsAvailable").asBoolean()).isFalse();
        page.get("items").forEach(item -> {
            assertThat(item.get("memberId").asLong()).isIn(ALICE, BOB);
            assertThat(item.get("memberEmail").isNull()).isTrue();
            assertThat(item.get("booking").get("bookableName").isNull()).isFalse();
        });
    }

    @Test
    @DisplayName("FR-ROLE-02: a member gets 403 from the admin list")
    void membersRefused_FRROLE02() {
        assertThat(http.get("/api/v1/admin/bookings", TestJwtKeys.memberToken(ALICE)).status()).isEqualTo(403);
    }
}
