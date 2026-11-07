package com.gridreserve.booking.concurrency;

import com.gridreserve.booking.booking.service.BookingMetrics;
import com.gridreserve.booking.booking.service.HoldExpiryService;
import com.gridreserve.booking.support.Http;
import com.gridreserve.booking.support.Http.Response;
import com.gridreserve.booking.support.MovableClock;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestJwtKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The concurrency guarantees (REQUIREMENTS §11, CONC-01..05) under real parallel HTTP requests against
 * PostgreSQL. Each test is repeated 20 times (CLAUDE.md §8.3) and asserts the exact outcome, the
 * database state, and that no transaction was killed for a deadlock (gridreserve.db.retries stays 0).
 * CONC-10 (two instances behind the gateway) is in backend/system-tests.
 */
@ServiceIntegrationTest
class ConcurrencyIT {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final long FIRST_MEMBER = 950_000L;

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MovableClock clock;

    @Autowired
    BookingMetrics metrics;

    @Autowired
    HoldExpiryService expiry;

    Http http;
    double lockFailuresBefore;

    @BeforeEach
    void setUp() {
        http = new Http(port);
        lockFailuresBefore = metrics.lockFailures();
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        jdbc.update("DELETE FROM booking WHERE member_id BETWEEN ? AND ?", FIRST_MEMBER, FIRST_MEMBER + 99);
    }

    private long bookable(String name) {
        return jdbc.queryForObject("SELECT id FROM bookable WHERE name = ?", Long.class, name);
    }

    private static Instant tomorrowAt(String localTime) {
        return LocalDate.now(KOLKATA).plusDays(1).atTime(LocalTime.parse(localTime)).atZone(KOLKATA).toInstant();
    }

    private Response hold(long memberId, long bookableId, Instant start, Instant end) {
        return http.post("/api/v1/bookings", TestJwtKeys.memberToken(memberId),
                Map.of("bookableId", bookableId, "start", start.toString(), "end", end.toString()));
    }

    /** "201" or the error code, so outcomes read like the requirement: 1 × 201, 49 × SLOT_TAKEN. */
    private static String outcome(Response response) {
        return response.status() == 201 ? "201" : response.code();
    }

    private static Map<String, Long> tally(List<String> outcomes) {
        return outcomes.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private int activeRows(long bookableId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM booking
                WHERE bookable_id = ? AND status IN ('HELD', 'CONFIRMED') AND member_id BETWEEN ? AND ?
                """, Integer.class, bookableId, FIRST_MEMBER, FIRST_MEMBER + 99);
    }

    private void assertNoDeadlocks() {
        assertThat(metrics.lockFailures() - lockFailuresBefore)
                .as("transactions rolled back for a lock failure, by SQLSTATE so far: %s", metrics.lockFailuresBySqlState())
                .isZero();
    }

    @RepeatedTest(20)
    @DisplayName("CONC-01: 50 members, one slot, at once → exactly 1 × 201 and 49 × 409 SLOT_TAKEN; one active row; < 10 s")
    void fiftyMembersOneSlot_CONC01() throws Exception {
        long court = bookable("Court A");
        Instant start = tomorrowAt("18:00");
        long began = System.nanoTime();

        List<String> outcomes = Parallel.run(50,
                index -> () -> outcome(hold(FIRST_MEMBER + index, court, start, start.plusSeconds(3600))));

        Duration took = Duration.ofNanos(System.nanoTime() - began);
        assertThat(tally(outcomes)).containsExactlyInAnyOrderEntriesOf(Map.of("201", 1L, "SLOT_TAKEN", 49L));
        assertThat(activeRows(court)).isEqualTo(1);
        assertThat(took).as("NFR-PERF-03").isLessThan(Duration.ofSeconds(10));
        assertNoDeadlocks();
    }

    @RepeatedTest(20)
    @DisplayName("CONC-02: 10 different but overlapping ranges on one room at once → exactly 1 succeeds, the rest SLOT_TAKEN")
    void overlappingRanges_CONC02() throws Exception {
        long room = bookable("Group Room 2");
        // Every range contains 12:00-12:30, so any two overlap: starts 10:00..12:00, ends 12:30 or 13:00.
        List<String[]> ranges = List.of(
                new String[] {"10:00", "12:30"}, new String[] {"10:00", "13:00"}, new String[] {"10:30", "12:30"},
                new String[] {"10:30", "13:00"}, new String[] {"11:00", "12:30"}, new String[] {"11:00", "13:00"},
                new String[] {"11:30", "12:30"}, new String[] {"11:30", "13:00"}, new String[] {"12:00", "12:30"},
                new String[] {"12:00", "13:00"});

        List<String> outcomes = Parallel.run(ranges.size(), index -> () -> outcome(hold(FIRST_MEMBER + index, room,
                tomorrowAt(ranges.get(index)[0]), tomorrowAt(ranges.get(index)[1]))));

        assertThat(tally(outcomes)).containsExactlyInAnyOrderEntriesOf(Map.of("201", 1L, "SLOT_TAKEN", 9L));
        assertThat(activeRows(room)).isEqualTo(1);
        assertNoDeadlocks();
    }

    @RepeatedTest(20)
    @DisplayName("CONC-03: one member, 10 seats, same time, at once → exactly 1 × 201 and 9 × 409 MEMBER_OVERLAP")
    void oneMemberTenSeats_CONC03() throws Exception {
        long member = FIRST_MEMBER;
        Instant start = tomorrowAt("12:00");

        List<String> outcomes = Parallel.run(10, index -> {
            long seat = bookable("Seat Q" + (index + 1));
            return () -> outcome(hold(member, seat, start, start.plusSeconds(3600)));
        });

        assertThat(tally(outcomes)).containsExactlyInAnyOrderEntriesOf(Map.of("201", 1L, "MEMBER_OVERLAP", 9L));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking WHERE member_id = ? AND status = 'HELD'",
                Integer.class, member)).isEqualTo(1);
        assertNoDeadlocks();
    }

    @RepeatedTest(20)
    @DisplayName("CONC-05: one member, 10 holds in one category at different times, limit 3 → exactly 3 succeed")
    void memberLimitUnderLoad_CONC05() throws Exception {
        long member = FIRST_MEMBER;
        long court = bookable("Court A");

        // 08:00, 09:00, ... 17:00: no overlaps, so only the limit can refuse.
        List<String> outcomes = Parallel.run(10, index -> {
            Instant start = tomorrowAt("08:00").plus(Duration.ofHours(index));
            return () -> outcome(hold(member, court, start, start.plusSeconds(3600)));
        });

        assertThat(tally(outcomes)).containsExactlyInAnyOrderEntriesOf(Map.of("201", 3L, "MEMBER_LIMIT_REACHED", 7L));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking WHERE member_id = ? AND status = 'HELD'",
                Integer.class, member)).isEqualTo(3);
        assertNoDeadlocks();
    }

    @RepeatedTest(20)
    @DisplayName("CONC-04: confirm racing the expiry job at the moment the hold runs out → exactly one wins, consistently")
    void confirmRacesExpiry_CONC04() throws Exception {
        long member = FIRST_MEMBER;
        Response held = hold(member, bookable("Court B"), tomorrowAt("15:00"), tomorrowAt("16:00"));
        long id = held.body().get("id").asLong();
        Instant expiresAt = Instant.parse(held.body().get("expiresAt").asString());
        // Land right on the edge: a few milliseconds before expiry, so either side can win.
        clock.moveTo(expiresAt.minusMillis(5));
        String token = TestJwtKeys.memberToken(member);

        List<String> results = Parallel.run(2, index -> index == 0
                ? () -> "confirm:" + http.post("/api/v1/bookings/" + id + "/confirm", token, null).status()
                : () -> "job:" + expiry.expire(id));

        String status = jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, id);
        assertThat(status).isIn("CONFIRMED", "EXPIRED");
        if (status.equals("CONFIRMED")) {
            assertThat(results).containsExactlyInAnyOrder("confirm:200", "job:false");
        } else {
            // The confirm lost: it either found the hold already expired or expired it itself.
            assertThat(results).contains("confirm:409");
        }
        assertNoDeadlocks();
    }
}
