package com.gridreserve.booking.concurrency;

import com.gridreserve.booking.booking.service.BookingMetrics;
import com.gridreserve.booking.support.Http;
import com.gridreserve.booking.support.Http.Response;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestCatalog;
import com.gridreserve.booking.support.TestJwtKeys;
import com.gridreserve.booking.support.TestBookings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONC-09: closing a bookable (or its venue) while holds on it are being created. Every hold must either
 * have committed before the deactivation (and stay valid) or be refused with {@code 422 BOOKABLE_INACTIVE};
 * no hold may commit after the deactivation. PostgreSQL's commit timestamps (track_commit_timestamp, on in
 * the test container) show the real commit order, so the test checks exactly that, not only the status codes.
 * 20 holds by different members on different hours plus one deactivation start at the same moment;
 * repeated 20 times (CLAUDE.md §8.3). The bookable race nearly always splits (some holds before, the rest
 * refused). The venue race nearly never does: each hold takes the venue {@code FOR SHARE} first and only then
 * queues on the bookable, so all 20 hold the shared lock at once and the deactivation ({@code FOR UPDATE})
 * waits for them all, or, rarely, comes first. Both orders are checked; the counts are printed at the end.
 */
@ServiceIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeactivationRaceIT {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final long FIRST_MEMBER = 980_000L;
    private static final int HOLDS = 20;

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    BookingMetrics metrics;

    Http http;
    TestCatalog catalog;
    // Per kind: races where the deactivation landed between holds (some before it, some refused), the
    // interesting case, out of all races.
    final Map<String, int[]> split = new java.util.TreeMap<>();
    long venueId;
    long courtId;

    @BeforeAll
    void createCourt() {
        http = new Http(port);
        catalog = new TestCatalog(http, jdbc);
        venueId = catalog.venue("SPORTS", "Race Turf");
        courtId = catalog.bookable(venueId, "COURT", "Race Court");
    }

    @AfterAll
    void removeCourt() {
        catalog.removeAll();
        split.forEach((kind, counts) -> System.out.printf(
                "CONC-09 (%s): in %d of %d races the deactivation landed between holds%n", kind, counts[0], counts[1]));
    }

    @BeforeEach
    void reopen() {
        TestBookings.delete(jdbc, "bookable_id = ?", courtId);
        http.post("/api/v1/venues/" + venueId + "/activate", TestJwtKeys.adminToken(), null);
        http.post("/api/v1/bookables/" + courtId + "/activate", TestJwtKeys.adminToken(), null);
    }

    /** Hold {@code index} takes hour {@code index} of tomorrow (00:00-20:00), so holds never conflict with each other. */
    private String hold(int index) {
        Instant start = LocalDate.now(KOLKATA).plusDays(1).atStartOfDay(KOLKATA).toInstant().plusSeconds(3600L * index);
        Response response = http.post("/api/v1/bookings", TestJwtKeys.memberToken(FIRST_MEMBER + index),
                Map.of("bookableId", courtId, "start", start.toString(), "end", start.plusSeconds(3600).toString()));
        return response.status() == 201 ? "201" : response.code();
    }

    private List<String> race(String deactivatePath) throws Exception {
        return Parallel.run(HOLDS + 1, index -> () -> index == HOLDS
                ? "deactivate " + http.post(deactivatePath, TestJwtKeys.adminToken(), null).status()
                : hold(index));
    }

    /** When the deactivation committed: the row it updated last. */
    private Instant committedAt(String table, long id) {
        return jdbc.queryForObject("SELECT pg_xact_commit_timestamp(xmin) FROM " + table + " WHERE id = ?",
                Timestamp.class, id).toInstant();
    }

    private List<Instant> holdCommits() {
        return jdbc.queryForList("SELECT pg_xact_commit_timestamp(xmin) FROM booking WHERE bookable_id = ?",
                Timestamp.class, courtId).stream().map(Timestamp::toInstant).toList();
    }

    private void assertSerialized(String kind, List<String> outcomes, Instant deactivated, double lockFailuresBefore) {
        assertThat(outcomes).contains("deactivate 200");
        List<String> holds = outcomes.stream().filter(outcome -> !outcome.startsWith("deactivate")).toList();
        assertThat(Set.copyOf(holds)).isSubsetOf(Set.of("201", "BOOKABLE_INACTIVE"));
        int[] counts = split.computeIfAbsent(kind, ignored -> new int[2]);
        counts[1]++;
        if (holds.contains("201") && holds.contains("BOOKABLE_INACTIVE")) {
            counts[0]++;
        }
        List<Instant> commits = holdCommits();
        assertThat(commits).as("one row per successful hold").hasSize((int) holds.stream().filter("201"::equals).count());
        assertThat(commits).as("every successful hold committed before the deactivation").allMatch(deactivated::isAfter);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking WHERE bookable_id = ? AND status <> 'HELD'",
                Integer.class, courtId)).as("earlier holds stay valid").isZero();
        assertThat(metrics.lockFailures() - lockFailuresBefore).as("deadlocks or serialization failures").isZero();
    }

    @RepeatedTest(20)
    @DisplayName("CONC-09: bookable closed while 20 holds race it → each hold committed before (and stays HELD) or got 422; none after")
    void bookableDeactivationRace_CONC09() throws Exception {
        double before = metrics.lockFailures();

        List<String> outcomes = race("/api/v1/bookables/" + courtId + "/deactivate");

        assertSerialized("bookable", outcomes, committedAt("bookable", courtId), before);
    }

    @RepeatedTest(20)
    @DisplayName("CONC-09: venue closed while 20 holds race on its bookable → the same, through the venue's lock")
    void venueDeactivationRace_CONC09() throws Exception {
        double before = metrics.lockFailures();

        List<String> outcomes = race("/api/v1/venues/" + venueId + "/deactivate");

        assertSerialized("venue", outcomes, committedAt("venue", venueId), before);
    }
}
