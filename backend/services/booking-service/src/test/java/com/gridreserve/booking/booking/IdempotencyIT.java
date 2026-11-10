package com.gridreserve.booking.booking;

import com.gridreserve.booking.booking.dto.CreateHoldRequest;
import com.gridreserve.booking.booking.job.IdempotencyCleanupJob;
import com.gridreserve.booking.booking.service.IdempotencyService;
import com.gridreserve.booking.concurrency.Parallel;
import com.gridreserve.booking.support.Http;
import com.gridreserve.booking.support.Http.Response;
import com.gridreserve.booking.support.MovableClock;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestBookings;
import com.gridreserve.booking.support.TestJwtKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code Idempotency-Key} on hold creation through HTTP (IDEM-01..07, FS-17, FS-18, US-11) and CONC-08 (many
 * parallel requests with one key). States the tests can't reach through the API (a request still running, one
 * that crashed, a key older than 24 hours) are set up as rows, as the service would have left them.
 */
@ServiceIntegrationTest
class IdempotencyIT {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final long MEMBER = 990_001L;
    private static final long OTHER = 990_002L;
    private static final long FIRST_RACER = 990_100L;

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MovableClock clock;

    @Autowired
    IdempotencyCleanupJob cleanup;

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
        jdbc.update("DELETE FROM idempotency_record WHERE member_id BETWEEN ? AND ?", MEMBER, FIRST_RACER + 99);
        TestBookings.delete(jdbc, "member_id BETWEEN ? AND ?", MEMBER, FIRST_RACER + 99);
    }

    private static Instant tomorrowAt(String localTime) {
        return LocalDate.now(KOLKATA).plusDays(1).atTime(LocalTime.parse(localTime)).atZone(KOLKATA).toInstant();
    }

    private Map<String, Object> slot(String from, String to) {
        return Map.of("bookableId", court, "start", tomorrowAt(from).toString(), "end", tomorrowAt(to).toString());
    }

    private Response hold(long member, Object body, String key) {
        return http.post("/api/v1/bookings", TestJwtKeys.memberToken(member), body,
                key == null ? Map.of() : Map.of(IdempotencyService.HEADER, key));
    }

    private int bookingsOf(long member) {
        return jdbc.queryForObject("SELECT count(*) FROM booking WHERE member_id = ?", Integer.class, member);
    }

    private String state(long member, String key) {
        List<String> states = jdbc.queryForList("SELECT state FROM idempotency_record WHERE member_id = ? AND idem_key = ?",
                String.class, member, key);
        return states.isEmpty() ? null : states.getFirst();
    }

    /** A row as the service would have left it, for a request on 18:00-19:00 tomorrow. */
    private void insertRecord(long member, String key, String state, Instant lockedUntil, Instant expiresAt,
                              Integer status, String body) {
        String hash = IdempotencyService.hash(new CreateHoldRequest(court, tomorrowAt("18:00"), tomorrowAt("19:00")));
        jdbc.update("INSERT INTO member (id, created_at) VALUES (?, now()) ON CONFLICT DO NOTHING", member);
        jdbc.update("""
                INSERT INTO idempotency_record (member_id, idem_key, request_hash, state, response_status, response_body,
                                                created_at, locked_until, expires_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), now(), ?, ?)
                """, member, key, hash, state, status, body, Timestamp.from(lockedUntil), Timestamp.from(expiresAt));
    }

    @Test
    @DisplayName("FS-17 / IDEM-03: the same key and request again → the first 201 again, marked replayed; one booking")
    void replay_FS17() {
        String key = UUID.randomUUID().toString();

        Response first = hold(MEMBER, slot("18:00", "19:00"), key);
        Response again = hold(MEMBER, slot("18:00", "19:00"), key);

        assertThat(first.status()).isEqualTo(201);
        assertThat(first.header("Idempotent-Replayed")).isNull();
        assertThat(again.status()).isEqualTo(201);
        assertThat(again.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(again.location()).isEqualTo(first.location());
        assertThat(again.body()).isEqualTo(first.body());
        assertThat(bookingsOf(MEMBER)).isEqualTo(1);
        assertThat(state(MEMBER, key)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("IDEM-03: \"same request\" means the same bookable and times, not the same bytes (field order, spacing, 18:00:00.000Z)")
    void sameRequestOtherJson_IDEM03() {
        String key = UUID.randomUUID().toString();
        hold(MEMBER, slot("18:00", "19:00"), key);
        String reformatted = """
                {  "end": "%s",
                   "start": "%s", "bookableId": %d }""".formatted(
                tomorrowAt("19:00"), tomorrowAt("18:00").toString().replace("Z", ".000Z"), court);

        Response again = hold(MEMBER, reformatted, key);

        assertThat(again.status()).isEqualTo(201);
        assertThat(again.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(bookingsOf(MEMBER)).isEqualTo(1);
    }

    @Test
    @DisplayName("FS-18 / IDEM-04: the same key with a different request → 422 IDEMPOTENCY_KEY_REUSED, no second booking")
    void keyReused_FS18() {
        String key = UUID.randomUUID().toString();
        hold(MEMBER, slot("18:00", "19:00"), key);

        Response other = hold(MEMBER, slot("20:00", "21:00"), key);

        assertThat(other.status()).isEqualTo(422);
        assertThat(other.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(bookingsOf(MEMBER)).isEqualTo(1);
    }

    @Test
    @DisplayName("IDEM-02: keys are per member: another member's request with the same key is a new request")
    void keysPerMember_IDEM02() {
        String key = UUID.randomUUID().toString();
        hold(MEMBER, slot("18:00", "19:00"), key);

        Response other = hold(OTHER, slot("20:00", "21:00"), key);

        assertThat(other.status()).isEqualTo(201);
        assertThat(other.header("Idempotent-Replayed")).isNull();
    }

    @Test
    @DisplayName("IDEM-06: a business refusal is stored: the retry gets the same 409 even after the slot became free")
    void refusalReplayed_IDEM06() {
        Response taken = hold(OTHER, slot("18:00", "19:00"), null);
        String key = UUID.randomUUID().toString();
        Response refused = hold(MEMBER, slot("18:00", "19:00"), key);
        http.post("/api/v1/bookings/" + taken.body().get("id").asLong() + "/cancel", TestJwtKeys.memberToken(OTHER), null);

        Response retried = hold(MEMBER, slot("18:00", "19:00"), key);

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("SLOT_TAKEN");
        assertThat(retried.status()).isEqualTo(409);
        assertThat(retried.code()).isEqualTo("SLOT_TAKEN");
        assertThat(bookingsOf(MEMBER)).isZero();
        // A new intent (a new key) books the now free slot.
        assertThat(hold(MEMBER, slot("18:00", "19:00"), UUID.randomUUID().toString()).status()).isEqualTo(201);
    }

    @Test
    @DisplayName("IDEM-05: while the first request is still running (lease valid) → 409 IDEMPOTENCY_IN_PROGRESS")
    void inProgress_IDEM05() {
        insertRecord(MEMBER, "running", "IN_PROGRESS", clock.instant().plusSeconds(20), clock.instant().plus(Duration.ofHours(24)),
                null, null);

        Response response = hold(MEMBER, slot("18:00", "19:00"), "running");

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.code()).isEqualTo("IDEMPOTENCY_IN_PROGRESS");
        assertThat(bookingsOf(MEMBER)).isZero();
    }

    @Test
    @DisplayName("IDEM-05: the first request crashed and its 30 s lease ran out → the retry takes the key over and books")
    void takeOver_IDEM05() {
        insertRecord(MEMBER, "crashed", "IN_PROGRESS", clock.instant().minusSeconds(1), clock.instant().plus(Duration.ofHours(24)),
                null, null);

        Response response = hold(MEMBER, slot("18:00", "19:00"), "crashed");

        assertThat(response.status()).isEqualTo(201);
        assertThat(state(MEMBER, "crashed")).isEqualTo("COMPLETED");
        assertThat(bookingsOf(MEMBER)).isEqualTo(1);
    }

    @Test
    @DisplayName("IDEM-07: a key older than 24 hours counts as unused, even before the cleanup removed it")
    void expiredKeyIsNew_IDEM07() {
        insertRecord(MEMBER, "old", "COMPLETED", clock.instant().minus(Duration.ofHours(25)),
                clock.instant().minus(Duration.ofHours(1)), 201, "{\"id\": 1}");

        Response response = hold(MEMBER, slot("20:00", "21:00"), "old");

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.header("Idempotent-Replayed")).isNull();
        assertThat(bookingsOf(MEMBER)).isEqualTo(1);
    }

    @Test
    @DisplayName("IDEM-07: the cleanup job deletes keys older than 24 hours and keeps the rest")
    void cleanupJob_IDEM07() {
        insertRecord(MEMBER, "expired", "COMPLETED", clock.instant().minus(Duration.ofHours(25)),
                clock.instant().minusSeconds(1), 409, "{\"code\": \"SLOT_TAKEN\", \"message\": \"x\"}");
        insertRecord(MEMBER, "fresh", "COMPLETED", clock.instant(), clock.instant().plus(Duration.ofHours(23)),
                409, "{\"code\": \"SLOT_TAKEN\", \"message\": \"x\"}");

        assertThat(cleanup.run()).isGreaterThanOrEqualTo(1);

        assertThat(state(MEMBER, "expired")).isNull();
        assertThat(state(MEMBER, "fresh")).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("IDEM-01: a key that is too long or has other characters → 400 VALIDATION_FAILED on Idempotency-Key; no key works as before")
    void keyFormat_IDEM01() {
        Response tooLong = hold(MEMBER, slot("18:00", "19:00"), "k".repeat(65));
        Response spaces = hold(MEMBER, slot("18:00", "19:00"), "has spaces");

        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(tooLong.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("Idempotency-Key");
        assertThat(spaces.status()).isEqualTo(400);
        assertThat(bookingsOf(MEMBER)).isZero();
        assertThat(hold(MEMBER, slot("18:00", "19:00"), null).status()).isEqualTo(201);
    }

    @RepeatedTest(20)
    @DisplayName("CONC-08: 20 parallel requests with one key → exactly one booking; the others replay it or see 409 in progress")
    void parallelSameKey_CONC08() throws Exception {
        String key = UUID.randomUUID().toString();

        List<String> outcomes = Parallel.run(20, index -> () -> {
            Response response = hold(FIRST_RACER, slot("18:00", "19:00"), key);
            if (response.status() == 201) {
                return "true".equals(response.header("Idempotent-Replayed")) ? "replayed" : "created";
            }
            return response.code();
        });

        Map<String, Long> tally = outcomes.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        System.out.println("CONC-08: " + new java.util.TreeMap<>(tally));
        assertThat(tally.get("created")).as("%s", tally).isEqualTo(1);
        assertThat(tally.keySet()).isSubsetOf("created", "replayed", "IDEMPOTENCY_IN_PROGRESS");
        assertThat(bookingsOf(FIRST_RACER)).isEqualTo(1);
        assertThat(state(FIRST_RACER, key)).isEqualTo("COMPLETED");
    }
}
