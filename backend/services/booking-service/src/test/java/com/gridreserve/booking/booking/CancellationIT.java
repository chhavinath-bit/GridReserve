package com.gridreserve.booking.booking;

import com.gridreserve.booking.support.Http;
import com.gridreserve.booking.support.Http.Response;
import com.gridreserve.booking.support.MovableClock;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestJwtKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cancellation policy (CXL-01..06) at its edges, with the service clock moved to exact moments. Court A
 * is in Sports: cutoff 2 hours. Tokens are issued at the moved "now", because the service checks token
 * expiry with the same clock.
 */
@ServiceIntegrationTest
class CancellationIT {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final long MEMBER = 930_001L;
    private static final long ADMIN = 1L;
    private static final Duration CUTOFF = Duration.ofHours(2);

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MovableClock clock;

    Http http;
    Instant start;
    Instant end;

    @BeforeEach
    void setUp() {
        http = new Http(port);
        // Tomorrow 10:00-11:00 at the court.
        start = LocalDate.now(KOLKATA).plusDays(1).atTime(10, 0).atZone(KOLKATA).toInstant();
        end = start.plus(Duration.ofHours(1));
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        jdbc.update("DELETE FROM booking WHERE member_id IN (?, ?)", MEMBER, ADMIN);
    }

    private String member() {
        return TestJwtKeys.sign(TestJwtKeys.KEYS, TestJwtKeys.KEY_ID,
                TestJwtKeys.claims(clock.instant()).subject(Long.toString(MEMBER)).build());
    }

    private String admin() {
        return TestJwtKeys.sign(TestJwtKeys.KEYS, TestJwtKeys.KEY_ID,
                TestJwtKeys.claims(clock.instant()).subject(Long.toString(ADMIN)).claim("role", "ADMIN").build());
    }

    private long court() {
        return jdbc.queryForObject("SELECT id FROM bookable WHERE name = 'Court A'", Long.class);
    }

    private long hold() {
        Response held = http.post("/api/v1/bookings", member(),
                Map.of("bookableId", court(), "start", start.toString(), "end", end.toString()));
        assertThat(held.status()).isEqualTo(201);
        return held.body().get("id").asLong();
    }

    private long confirmedBooking() {
        long id = hold();
        assertThat(http.post("/api/v1/bookings/" + id + "/confirm", member(), null).status()).isEqualTo(200);
        return id;
    }

    private Response cancel(String token, long id) {
        return http.post("/api/v1/bookings/" + id + "/cancel", token, null);
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("CXL-02: a member cancels a confirmed booking one minute before the cutoff → 200")
    void beforeCutoff_CXL02() {
        long id = confirmedBooking();
        clock.moveTo(start.minus(CUTOFF).minus(Duration.ofMinutes(1)));

        Response response = cancel(member(), id);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("cancelledBy").asString()).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("CXL-02: one minute after the cutoff → 422 CUTOFF_PASSED; the booking stays CONFIRMED")
    void afterCutoff_CXL02() {
        long id = confirmedBooking();
        clock.moveTo(start.minus(CUTOFF).plus(Duration.ofMinutes(1)));

        Response response = cancel(member(), id);
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.code()).isEqualTo("CUTOFF_PASSED");
        assertThat(response.body().get("message").asString()).contains("2 hours before the start");
        assertThat(status(id)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("CXL-02: after the start, before the end, a member still gets 422 CUTOFF_PASSED")
    void memberDuringBooking_CXL02() {
        long id = confirmedBooking();
        clock.moveTo(start.plus(Duration.ofMinutes(10)));

        assertThat(cancel(member(), id).code()).isEqualTo("CUTOFF_PASSED");
    }

    @Test
    @DisplayName("CXL-01: a live hold can be cancelled inside the cutoff window")
    void holdInsideCutoff_CXL01() {
        clock.moveTo(start.minus(Duration.ofHours(1)));
        long id = hold();

        Response response = cancel(member(), id);
        assertThat(response.status()).isEqualTo(200);
        assertThat(status(id)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("CXL-03: an admin cancels inside the cutoff and even after the start, until the end")
    void adminUntilEnd_CXL03() {
        long id = confirmedBooking();
        clock.moveTo(start.plus(Duration.ofMinutes(30)));

        Response response = cancel(admin(), id);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("cancelledBy").asString()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("CXL-04: once the booking has ended, nobody can cancel it → 409 INVALID_STATE")
    void afterEnd_CXL04() {
        long id = confirmedBooking();
        clock.moveTo(end.plus(Duration.ofMinutes(1)));

        Response asMember = cancel(member(), id);
        assertThat(asMember.status()).isEqualTo(409);
        assertThat(asMember.code()).isEqualTo("INVALID_STATE");
        assertThat(asMember.body().get("message").asString()).isEqualTo("This booking has already ended.");
        assertThat(cancel(admin(), id).code()).isEqualTo("INVALID_STATE");
        assertThat(status(id)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("CXL-06: cancelling twice → 409 INVALID_STATE")
    void cancelTwice_CXL06() {
        long id = confirmedBooking();
        assertThat(cancel(member(), id).status()).isEqualTo(200);
        assertThat(cancel(member(), id).code()).isEqualTo("INVALID_STATE");
    }
}
