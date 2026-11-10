package com.gridreserve.booking.availability;

import com.gridreserve.booking.member.service.MemberProvisioner;
import com.gridreserve.booking.support.Http;
import com.gridreserve.booking.support.Http.Response;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestJwtKeys;
import com.gridreserve.booking.support.TestBookings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Availability over real HTTP against PostgreSQL (FR-AV-01..06). Dates are relative to today in the
 * venue's zone, so the tests work on any day; bookings are inserted directly and removed afterwards.
 */
@ServiceIntegrationTest
class AvailabilityIT {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final long MEMBER_A = 910_001L;
    private static final long MEMBER_B = 910_002L;
    private static final long MEMBER_C = 910_003L;

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MemberProvisioner members;

    Http http;
    String member;

    @BeforeEach
    void setUp() {
        http = new Http(port);
        member = TestJwtKeys.memberToken();
        members.ensure(MEMBER_A);
        members.ensure(MEMBER_B);
        members.ensure(MEMBER_C);
    }

    @AfterEach
    void cleanUp() {
        TestBookings.delete(jdbc, "member_id IN (?, ?, ?)", MEMBER_A, MEMBER_B, MEMBER_C);
    }

    private long bookable(String name) {
        return jdbc.queryForObject("SELECT id FROM bookable WHERE name = ?", Long.class, name);
    }

    private Response availability(long bookableId, LocalDate date) {
        return http.get("/api/v1/bookables/" + bookableId + "/availability?date=" + date, member);
    }

    /** Local start time ("HH:mm") → status, in slot order. */
    private static Map<String, String> statuses(Response response) {
        Map<String, String> statuses = new LinkedHashMap<>();
        response.body().get("slots").forEach(slot ->
                statuses.put(slot.get("localStart").asString().substring(11, 16), slot.get("status").asString()));
        return statuses;
    }

    private void book(long memberId, long bookableId, LocalDate date, ZoneId zone, LocalTime start, LocalTime end,
                      int bufferMinutes, String status, Instant expiresAt) {
        Instant startAt = date.atTime(start).atZone(zone).toInstant();
        Instant endAt = date.atTime(end).atZone(zone).toInstant();
        jdbc.update("""
                INSERT INTO booking (member_id, bookable_id, start_time, end_time, occupied_until, status,
                                     expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
                """, memberId, bookableId, Timestamp.from(startAt), Timestamp.from(endAt),
                Timestamp.from(endAt.plusSeconds(bufferMinutes * 60L)), status,
                expiresAt == null ? null : Timestamp.from(expiresAt));
    }

    @Test
    @DisplayName("FR-AV-01/02/06: every slot of the local day, in UTC and venue time, with the picker's rules")
    void slotsOfTheDay_FRAV01() {
        LocalDate tomorrow = LocalDate.now(KOLKATA).plusDays(1);

        Response response = availability(bookable("Court A"), tomorrow);

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = response.body();
        assertThat(body.get("closed").asBoolean()).isFalse();
        assertThat(body.get("venueTimeZone").asString()).isEqualTo("Asia/Kolkata");
        assertThat(body.get("label").asString()).isEqualTo("Court");
        assertThat(body.get("slotMinutes").asInt()).isEqualTo(60);
        assertThat(body.get("maxDurationMinutes").asInt()).isEqualTo(120);
        assertThat(statuses(response)).hasSize(17).containsEntry("06:00", "FREE").containsEntry("22:00", "FREE");
        JsonNode first = body.get("slots").get(0);
        assertThat(Instant.parse(first.get("start").asString()))
                .isEqualTo(tomorrow.atTime(6, 0).atZone(KOLKATA).toInstant());
    }

    @Test
    @DisplayName("FR-AV-03: a confirmed booking makes its slots TAKEN; the next slot stays FREE")
    void confirmedBookingTakesSlots_FRAV03() {
        long court = bookable("Court A");
        LocalDate tomorrow = LocalDate.now(KOLKATA).plusDays(1);
        book(MEMBER_A, court, tomorrow, KOLKATA, LocalTime.of(10, 0), LocalTime.of(12, 0), 0, "CONFIRMED", null);

        Map<String, String> statuses = statuses(availability(court, tomorrow));

        assertThat(statuses).containsEntry("09:00", "FREE").containsEntry("10:00", "TAKEN")
                .containsEntry("11:00", "TAKEN").containsEntry("12:00", "FREE");
    }

    @Test
    @DisplayName("FR-AV-03: the buffer after a booking also blocks slots (trainer: 45 min + 15 min buffer)")
    void bufferBlocksFollowingSlot_FRAV03() {
        long trainer = bookable("Asha (personal training)");
        LocalDate tomorrow = LocalDate.now(LONDON).plusDays(1);
        book(MEMBER_A, trainer, tomorrow, LONDON, LocalTime.of(10, 0), LocalTime.of(10, 45), 15, "CONFIRMED", null);

        Map<String, String> statuses = statuses(availability(trainer, tomorrow));

        assertThat(statuses).containsEntry("09:45", "FREE").containsEntry("10:00", "TAKEN")
                .containsEntry("10:30", "TAKEN").containsEntry("10:45", "TAKEN") // the buffer
                .containsEntry("11:00", "FREE");
    }

    @Test
    @DisplayName("FR-AV-03/FR-EXP-04: a live hold blocks; an expired hold the job has not cleaned up does not")
    void expiredHoldIsFree_FRAV03() {
        long court = bookable("Court A");
        LocalDate tomorrow = LocalDate.now(KOLKATA).plusDays(1);
        Instant now = Instant.now();
        book(MEMBER_A, court, tomorrow, KOLKATA, LocalTime.of(14, 0), LocalTime.of(15, 0), 0, "HELD",
                now.minusSeconds(60));
        book(MEMBER_B, court, tomorrow, KOLKATA, LocalTime.of(16, 0), LocalTime.of(17, 0), 0, "HELD",
                now.plusSeconds(300));
        book(MEMBER_C, court, tomorrow, KOLKATA, LocalTime.of(18, 0), LocalTime.of(19, 0), 0, "CANCELLED", null);

        Map<String, String> statuses = statuses(availability(court, tomorrow));

        assertThat(statuses).containsEntry("14:00", "FREE")    // hold ran out a minute ago
                .containsEntry("16:00", "TAKEN")               // hold still running
                .containsEntry("18:00", "FREE");               // cancelled bookings never block
    }

    @Test
    @DisplayName("FR-AV-05: availability never says who holds a slot")
    void noMemberInResponse_FRAV05() {
        long court = bookable("Court A");
        LocalDate tomorrow = LocalDate.now(KOLKATA).plusDays(1);
        book(MEMBER_A, court, tomorrow, KOLKATA, LocalTime.of(10, 0), LocalTime.of(11, 0), 0, "CONFIRMED", null);

        Response response = availability(court, tomorrow);

        assertThat(response.body().toString()).doesNotContain("member").doesNotContain(String.valueOf(MEMBER_A));
    }

    @Test
    @DisplayName("FR-AV-04: every slot of a past date is PAST")
    void pastDate_FRAV04() {
        Response response = availability(bookable("Court A"), LocalDate.now(KOLKATA).minusDays(1));

        assertThat(response.status()).isEqualTo(200);
        assertThat(statuses(response).values()).containsOnly("PAST");
    }

    @Test
    @DisplayName("FR-AV-04/BR-07: today + 6 is open; today + 7 → 422 BEYOND_HORIZON")
    void horizon_FRAV04() {
        long court = bookable("Court A");
        LocalDate today = LocalDate.now(KOLKATA);

        assertThat(availability(court, today.plusDays(6)).status()).isEqualTo(200);
        Response beyond = availability(court, today.plusDays(7));
        assertThat(beyond.status()).isEqualTo(422);
        assertThat(beyond.code()).isEqualTo("BEYOND_HORIZON");
    }

    @Test
    @DisplayName("FR-AV-01: a closed weekday → 200 with closed: true and no slots")
    void closedWeekday_FRAV01() {
        LocalDate sunday = LocalDate.now(KOLKATA).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        Response response = availability(bookable("Dr. Rao"), sunday); // the clinic is closed on Sundays

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("closed").asBoolean()).isTrue();
        assertThat(response.body().get("slots")).isEmpty();
    }

    @Test
    @DisplayName("FR-CAT-04: own hours replace the venue's (Dr. Mehta: Tue and Thu 10:00-14:00 only)")
    void ownHours_FRCAT04() {
        long mehta = bookable("Dr. Mehta");
        LocalDate today = LocalDate.now(KOLKATA);
        LocalDate monday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate tuesday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));

        assertThat(availability(mehta, monday).body().get("closed").asBoolean()).isTrue(); // clinic is open Mondays
        Map<String, String> tuesdaySlots = statuses(availability(mehta, tuesday));
        assertThat(tuesdaySlots).hasSize(8);
        assertThat(tuesdaySlots.keySet()).first().isEqualTo("10:00");
    }

    @Test
    @DisplayName("FR-CAT-10: an inactive bookable's availability is 404 for members")
    void inactiveBookable_FRCAT10() {
        long court = bookable("Court B");
        jdbc.update("UPDATE bookable SET active = false WHERE id = ?", court);
        try {
            Response response = availability(court, LocalDate.now(KOLKATA).plusDays(1));
            assertThat(response.status()).isEqualTo(404);
        } finally {
            jdbc.update("UPDATE bookable SET active = true WHERE id = ?", court);
        }
    }

    @Test
    @DisplayName("FR-CAT-01: a CAPACITY bookable → 422 WRONG_BOOKING_MODEL until Phase 2")
    void capacityNotSupported_FRCAT01() {
        jdbc.update("""
                INSERT INTO bookable (venue_id, bookable_type_id, name, created_at, updated_at)
                SELECT v.id, t.id, 'Test Reading Hall', now(), now()
                FROM venue v, bookable_type t
                WHERE v.name = 'Central Library' AND t.code = 'HALL'
                """);
        try {
            Response response = availability(bookable("Test Reading Hall"), LocalDate.now(KOLKATA).plusDays(1));
            assertThat(response.status()).isEqualTo(422);
            assertThat(response.code()).isEqualTo("WRONG_BOOKING_MODEL");
        } finally {
            jdbc.update("DELETE FROM bookable WHERE name = 'Test Reading Hall'");
        }
    }

    @Test
    @DisplayName("API-06: unknown bookable → 404; a bad or missing date → 400 VALIDATION_FAILED")
    void badRequests() {
        assertThat(availability(999_999L, LocalDate.now(KOLKATA)).status()).isEqualTo(404);

        Response badDate = http.get("/api/v1/bookables/" + bookable("Court A") + "/availability?date=tomorrow", member);
        assertThat(badDate.status()).isEqualTo(400);
        assertThat(badDate.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(http.get("/api/v1/bookables/" + bookable("Court A") + "/availability", member).status())
                .isEqualTo(400);
    }
}
