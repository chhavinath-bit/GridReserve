package com.gridreserve.booking.audit;

import com.gridreserve.booking.audit.service.Actor;
import com.gridreserve.booking.audit.service.AuditService;
import com.gridreserve.booking.booking.job.HoldExpiryJob;
import com.gridreserve.booking.support.Http;
import com.gridreserve.booking.support.Http.Response;
import com.gridreserve.booking.support.MovableClock;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestBookings;
import com.gridreserve.booking.support.TestJwtKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The booking audit log (FR-AUD-01..03): one row per state change, with who made it, written in the same
 * transaction, nothing for a refused change, and the admin endpoint that reads it.
 */
@ServiceIntegrationTest
class AuditIT {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final long MEMBER = 995_001L;
    private static final long OTHER = 995_002L;
    private static final long ADMIN = 1L;   // TestJwtKeys.adminToken()

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MovableClock clock;

    @Autowired
    HoldExpiryJob expiryJob;

    @Autowired
    AuditService audit;

    Http http;
    long court;

    @BeforeEach
    void setUp() {
        http = new Http(port);
        court = jdbc.queryForObject("SELECT id FROM bookable WHERE name = 'Court A'", Long.class);
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        TestBookings.delete(jdbc, "member_id IN (?, ?)", MEMBER, OTHER);
    }

    private static Instant tomorrowAt(String localTime) {
        return LocalDate.now(KOLKATA).plusDays(1).atTime(LocalTime.parse(localTime)).atZone(KOLKATA).toInstant();
    }

    private Response hold(long member, String from, String to) {
        return http.post("/api/v1/bookings", TestJwtKeys.memberToken(member),
                Map.of("bookableId", court, "start", tomorrowAt(from).toString(), "end", tomorrowAt(to).toString()));
    }

    private static long id(Response response) {
        return response.body().get("id").asLong();
    }

    /** "from→to actor user reason" per row, oldest first, through the admin endpoint. */
    private List<String> trail(long bookingId) {
        Response response = http.get("/api/v1/admin/bookings/" + bookingId + "/audit", TestJwtKeys.adminToken());
        assertThat(response.status()).isEqualTo(200);
        List<String> rows = new ArrayList<>();
        for (JsonNode row : response.body()) {
            rows.add(text(row.get("fromStatus")) + "→" + row.get("toStatus").asString() + " "
                    + row.get("actorType").asString() + " " + text(row.get("actorUserId")) + " " + text(row.get("reason")));
        }
        return rows;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "-" : node.asString();
    }

    private int auditRows() {
        return jdbc.queryForObject("SELECT count(*) FROM booking_audit", Integer.class);
    }

    @Test
    @DisplayName("FR-AUD-01/03: hold → confirm → cancel by the member: three rows in time order, with the reason")
    void memberLifecycle_FRAUD01() {
        long booking = id(hold(MEMBER, "18:00", "19:00"));
        http.post("/api/v1/bookings/" + booking + "/confirm", TestJwtKeys.memberToken(MEMBER), null);
        http.post("/api/v1/bookings/" + booking + "/cancel", TestJwtKeys.memberToken(MEMBER), Map.of("reason", "Plans changed"));

        assertThat(trail(booking)).containsExactly(
                "-→HELD MEMBER " + MEMBER + " -",
                "HELD→CONFIRMED MEMBER " + MEMBER + " -",
                "CONFIRMED→CANCELLED MEMBER " + MEMBER + " Plans changed");
    }

    @Test
    @DisplayName("FR-AUD-01: an admin cancelling a member's booking is recorded as ADMIN with the admin's ID")
    void adminCancel_FRAUD01() {
        long booking = id(hold(MEMBER, "18:00", "19:00"));

        http.post("/api/v1/bookings/" + booking + "/cancel", TestJwtKeys.adminToken(), Map.of("reason", "Court repairs"));

        assertThat(trail(booking)).last().isEqualTo("HELD→CANCELLED ADMIN " + ADMIN + " Court repairs");
    }

    @Test
    @DisplayName("FR-AUD-01: the expiry job records a run-out hold as EXPIRED by SYSTEM, with no user")
    void jobExpiry_FRAUD01() {
        long booking = id(hold(MEMBER, "18:00", "19:00"));
        clock.advance(Duration.ofMinutes(6));

        expiryJob.run();

        assertThat(trail(booking)).last().isEqualTo("HELD→EXPIRED SYSTEM - -");
    }

    @Test
    @DisplayName("FR-AUD-02: a late confirm records the expiry (SYSTEM) although it answers 409 HOLD_EXPIRED")
    void expiryOnConfirm_FRAUD02() {
        long booking = id(hold(MEMBER, "18:00", "19:00"));
        clock.advance(Duration.ofMinutes(6));

        Response late = http.post("/api/v1/bookings/" + booking + "/confirm", TestJwtKeys.memberToken(MEMBER), null);

        assertThat(late.code()).isEqualTo("HOLD_EXPIRED");
        assertThat(trail(booking)).containsExactly("-→HELD MEMBER " + MEMBER + " -", "HELD→EXPIRED SYSTEM - -");
    }

    @Test
    @DisplayName("FR-AUD-01: a run-out hold swept away by someone else's hold is recorded EXPIRED by SYSTEM")
    void expiryBySweep_FRAUD01() {
        long first = id(hold(MEMBER, "18:00", "19:00"));
        clock.advance(Duration.ofMinutes(6));

        long second = id(hold(OTHER, "18:00", "19:00"));

        assertThat(trail(first)).last().isEqualTo("HELD→EXPIRED SYSTEM - -");
        assertThat(trail(second)).containsExactly("-→HELD MEMBER " + OTHER + " -");
    }

    @Test
    @DisplayName("FR-AUD-02: a refused change writes no row (taken slot, cancel after the cutoff, confirm twice)")
    void refusalsLeaveNoRows_FRAUD02() {
        long booking = id(hold(MEMBER, "18:00", "19:00"));
        http.post("/api/v1/bookings/" + booking + "/confirm", TestJwtKeys.memberToken(MEMBER), null);
        int before = auditRows();

        assertThat(hold(OTHER, "18:00", "19:00").code()).isEqualTo("SLOT_TAKEN");
        assertThat(http.post("/api/v1/bookings/" + booking + "/confirm", TestJwtKeys.memberToken(MEMBER), null).code())
                .isEqualTo("INVALID_STATE");
        clock.moveTo(tomorrowAt("17:00"));   // one hour before the start: inside the 2-hour cutoff
        // Tokens are checked against the application's clock, so this one is issued at the moved time.
        String later = TestJwtKeys.sign(TestJwtKeys.KEYS, TestJwtKeys.KEY_ID,
                TestJwtKeys.claims(clock.instant()).subject(Long.toString(MEMBER)).build());
        assertThat(http.post("/api/v1/bookings/" + booking + "/cancel", later, null).code()).isEqualTo("CUTOFF_PASSED");

        assertThat(auditRows()).isEqualTo(before);
    }

    @Test
    @DisplayName("FR-AUD-02: recording outside a transaction is refused, so a row can never be written apart from its change")
    void auditNeedsTheChangesTransaction_FRAUD02() {
        assertThatThrownBy(() -> audit.record(1L, null, "HELD", Actor.SYSTEM, null, Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("FR-AUD-03: the audit endpoint is ADMIN only (403 for members) and 404 for an unknown booking")
    void endpointAccess_FRAUD03() {
        long booking = id(hold(MEMBER, "18:00", "19:00"));

        assertThat(http.get("/api/v1/admin/bookings/" + booking + "/audit", TestJwtKeys.memberToken(MEMBER)).status())
                .isEqualTo(403);
        assertThat(http.get("/api/v1/admin/bookings/99999999/audit", TestJwtKeys.adminToken()).status()).isEqualTo(404);
    }
}
