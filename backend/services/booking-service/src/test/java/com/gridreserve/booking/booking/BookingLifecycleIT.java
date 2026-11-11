package com.gridreserve.booking.booking;

import com.gridreserve.booking.support.Http;
import com.gridreserve.booking.support.Http.Response;
import com.gridreserve.booking.support.MovableClock;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestJwtKeys;
import com.gridreserve.booking.support.TestBookings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Hold, confirm and cancel over real HTTP against PostgreSQL (FR-BK-01..06, BR-10..13). Times are
 * tomorrow in the venue's own zone (Sunrise Turf: Asia/Kolkata, IronWorks: Europe/London), so the tests
 * work on any day and fall inside opening hours (BR-02).
 * The service clock is a {@link MovableClock}: moving it forward lets a hold run out without waiting.
 */
@ServiceIntegrationTest
class BookingLifecycleIT {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final String TRAINER = "Asha (personal training)";
    private static final long MEMBER_A = 920_001L;
    private static final long MEMBER_B = 920_002L;
    private static final long ADMIN = 1L;
    private static final Duration PAST_THE_HOLD = Duration.ofMinutes(6);

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MovableClock clock;

    Http http;
    String memberA;
    String memberB;
    String admin;

    @BeforeEach
    void setUp() {
        http = new Http(port);
        memberA = TestJwtKeys.memberToken(MEMBER_A);
        memberB = TestJwtKeys.memberToken(MEMBER_B);
        admin = TestJwtKeys.adminToken();
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        TestBookings.delete(jdbc, "member_id IN (?, ?, ?)", MEMBER_A, MEMBER_B, ADMIN);
    }

    private long bookable(String name) {
        return jdbc.queryForObject("SELECT id FROM bookable WHERE name = ?", Long.class, name);
    }

    private static Instant tomorrowAt(String localTime) {
        return tomorrowAt(localTime, KOLKATA);
    }

    private static Instant tomorrowAt(String localTime, ZoneId zone) {
        return LocalDate.now(zone).plusDays(1).atTime(LocalTime.parse(localTime)).atZone(zone).toInstant();
    }

    private static Map<String, Object> request(long bookableId, Instant start, Instant end) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bookableId", bookableId);
        body.put("start", start.toString());
        body.put("end", end.toString());
        return body;
    }

    private Response hold(String token, String bookableName, String from, String to) {
        return hold(token, bookableName, from, to, KOLKATA);
    }

    private Response hold(String token, String bookableName, String from, String to, ZoneId zone) {
        return http.post("/api/v1/bookings", token,
                request(bookable(bookableName), tomorrowAt(from, zone), tomorrowAt(to, zone)));
    }

    private Response confirm(String token, long bookingId) {
        return http.post("/api/v1/bookings/" + bookingId + "/confirm", token, null);
    }

    private Response cancel(String token, long bookingId, String reason) {
        return http.post("/api/v1/bookings/" + bookingId + "/cancel", token,
                reason == null ? null : Map.of("reason", reason));
    }

    private static long id(Response response) {
        return response.body().get("id").asLong();
    }

    private String status(long bookingId) {
        return jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, bookingId);
    }

    private Instant timestamp(String column, long bookingId) {
        Timestamp value = jdbc.queryForObject("SELECT " + column + " FROM booking WHERE id = ?", Timestamp.class,
                bookingId);
        return value == null ? null : value.toInstant();
    }

    private int activeRows(String bookableName) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM booking
                WHERE bookable_id = ? AND status IN ('HELD', 'CONFIRMED') AND member_id IN (?, ?, ?)
                """, Integer.class, bookable(bookableName), MEMBER_A, MEMBER_B, ADMIN);
    }

    @Nested
    @DisplayName("Hold")
    class Hold {

        @Test
        @DisplayName("FR-BK-01/07: 201 HELD for 5 minutes, Location header, names from the catalog; member row created")
        void holdCreated_FRBK01() {
            Instant before = Instant.now();
            // Some clocks (Linux) have nanoseconds, PostgreSQL keeps microseconds: make sure "now" has nanos.
            clock.advance(Duration.ofNanos(123));
            Response response = hold(memberA, "Court A", "10:00", "11:00");

            assertThat(response.status()).isEqualTo(201);
            long id = id(response);
            assertThat(response.location()).isEqualTo("/api/v1/bookings/" + id);
            assertThat(response.body().get("status").asString()).isEqualTo("HELD");
            assertThat(response.body().get("bookableName").asString()).isEqualTo("Court A");
            assertThat(response.body().get("bookableLabel").asString()).isEqualTo("Court");
            assertThat(response.body().get("venueName").asString()).isEqualTo("Sunrise Turf");
            assertThat(response.body().get("venueCity").asString()).isEqualTo("Bengaluru");
            assertThat(response.body().get("venueCountryCode").asString()).isEqualTo("IN");
            assertThat(response.body().get("venueTimeZone").asString()).isEqualTo("Asia/Kolkata");
            assertThat(Instant.parse(response.body().get("start").asString())).isEqualTo(tomorrowAt("10:00"));
            assertThat(Instant.parse(response.body().get("expiresAt").asString()))
                    .isCloseTo(before.plus(Duration.ofMinutes(5)), within(Duration.ofSeconds(10)));
            assertThat(response.body().get("cancelledAt").isNull()).isTrue();
            // The response shows what was stored: a later read returns the same timestamps.
            Response read = http.get("/api/v1/bookings/" + id, memberA);
            assertThat(read.body().get("createdAt")).isEqualTo(response.body().get("createdAt"));
            assertThat(read.body().get("expiresAt")).isEqualTo(response.body().get("expiresAt"));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM member WHERE id = ?", Integer.class, MEMBER_A))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT member_id FROM booking WHERE id = ?", Long.class, id))
                    .isEqualTo(MEMBER_A);
        }

        @Test
        @DisplayName("BR-10: the same slot for another member → 409 SLOT_TAKEN; one active row")
        void overlapRejected_BR10() {
            assertThat(hold(memberA, "Court A", "10:00", "11:00").status()).isEqualTo(201);

            Response second = hold(memberB, "Court A", "10:00", "11:00");
            assertThat(second.status()).isEqualTo(409);
            assertThat(second.code()).isEqualTo("SLOT_TAKEN");
            assertThat(activeRows("Court A")).isEqualTo(1);
        }

        @Test
        @DisplayName("BR-12: back-to-back bookings do not overlap")
        void backToBackAllowed_BR12() {
            assertThat(hold(memberA, "Court A", "10:00", "11:00").status()).isEqualTo(201);
            assertThat(hold(memberB, "Court A", "11:00", "12:00").status()).isEqualTo(201);
        }

        @Test
        @DisplayName("BR-10: the buffer is part of the occupied time (Trainer: 45 min + 15 min buffer)")
        void bufferBlocksNextStart_BR10() {
            Response first = hold(memberA, TRAINER, "10:00", "10:45", LONDON);
            assertThat(first.status()).isEqualTo(201);
            assertThat(timestamp("occupied_until", id(first))).isEqualTo(tomorrowAt("11:00", LONDON));

            Response inBuffer = hold(memberB, TRAINER, "10:45", "11:30", LONDON);
            assertThat(inBuffer.status()).isEqualTo(409);
            assertThat(inBuffer.code()).isEqualTo("SLOT_TAKEN");
            assertThat(hold(memberB, TRAINER, "11:00", "11:45", LONDON).status()).isEqualTo(201);
        }

        @Test
        @DisplayName("FS-02 / BR-11: the same member at the same time on another bookable → 409 MEMBER_OVERLAP")
        void memberOverlapRejected_FS02() {
            assertThat(hold(memberA, "Court A", "10:00", "11:00").status()).isEqualTo(201);

            Response elsewhere = hold(memberA, "Court B", "10:00", "11:00");
            assertThat(elsewhere.status()).isEqualTo(409);
            assertThat(elsewhere.code()).isEqualTo("MEMBER_OVERLAP");
        }

        @Test
        @DisplayName("FS-21: another member's run-out hold does not block; it is marked EXPIRED")
        void staleHoldOnBookableDoesNotBlock_FS21() {
            long stale = id(hold(memberA, "Court A", "10:00", "11:00"));
            clock.advance(PAST_THE_HOLD);

            assertThat(hold(memberB, "Court A", "10:00", "11:00").status()).isEqualTo(201);
            assertThat(status(stale)).isEqualTo("EXPIRED");
        }

        @Test
        @DisplayName("FR-EXP-02: the member's own run-out hold does not block their next booking at that time")
        void ownStaleHoldDoesNotBlock_FREXP02() {
            long stale = id(hold(memberA, "Court A", "10:00", "11:00"));
            clock.advance(PAST_THE_HOLD);

            assertThat(hold(memberA, "Court B", "10:00", "11:00").status()).isEqualTo(201);
            assertThat(status(stale)).isEqualTo("EXPIRED");
        }
    }

    @Nested
    @DisplayName("Rules checked before the insert (BR-14 order)")
    class Rules {

        @Test
        @DisplayName("BR-01: end not after start → 400 VALIDATION_FAILED; missing fields → 400 with field errors")
        void timesInvalid_BR01() {
            Response reversed = hold(memberA, "Court A", "11:00", "10:00");
            assertThat(reversed.status()).isEqualTo(400);
            assertThat(reversed.code()).isEqualTo("VALIDATION_FAILED");

            Response missing = http.post("/api/v1/bookings", memberA, Map.of("bookableId", bookable("Court A")));
            assertThat(missing.status()).isEqualTo(400);
            assertThat(missing.code()).isEqualTo("VALIDATION_FAILED");
            List<String> fields = missing.body().get("fieldErrors").valueStream()
                    .map(error -> error.get("field").asString()).toList();
            assertThat(fields).containsExactlyInAnyOrder("start", "end");
        }

        @Test
        @DisplayName("BR-05: a start in the past → 422 START_IN_PAST")
        void startInPast_BR05() {
            Instant yesterday = tomorrowAt("10:00").minus(Duration.ofDays(2));
            Response response = http.post("/api/v1/bookings", memberA,
                    request(bookable("Court A"), yesterday, yesterday.plus(Duration.ofHours(1))));
            assertThat(response.status()).isEqualTo(422);
            assertThat(response.code()).isEqualTo("START_IN_PAST");
        }

        @Test
        @DisplayName("BR-06 before BR-05: an inactive bookable → 422 BOOKABLE_INACTIVE, even for a past start")
        void inactiveBookable_BR06() {
            long court = bookable("Court A");
            jdbc.update("UPDATE bookable SET active = false WHERE id = ?", court);
            try {
                assertThat(hold(memberA, "Court A", "10:00", "11:00").code()).isEqualTo("BOOKABLE_INACTIVE");

                Instant yesterday = tomorrowAt("10:00").minus(Duration.ofDays(2));
                Response past = http.post("/api/v1/bookings", memberA,
                        request(court, yesterday, yesterday.plus(Duration.ofHours(1))));
                assertThat(past.status()).isEqualTo(422);
                assertThat(past.code()).isEqualTo("BOOKABLE_INACTIVE");
            } finally {
                jdbc.update("UPDATE bookable SET active = true WHERE id = ?", court);
            }
        }

        @Test
        @DisplayName("BR-06: a bookable of an inactive venue → 422 BOOKABLE_INACTIVE")
        void inactiveVenue_BR06() {
            jdbc.update("UPDATE venue SET active = false WHERE name = 'Sunrise Turf'");
            try {
                Response response = hold(memberA, "Court A", "10:00", "11:00");
                assertThat(response.status()).isEqualTo(422);
                assertThat(response.code()).isEqualTo("BOOKABLE_INACTIVE");
            } finally {
                jdbc.update("UPDATE venue SET active = true WHERE name = 'Sunrise Turf'");
            }
        }

        @Test
        @DisplayName("BR-09: a CAPACITY bookable → 422 WRONG_BOOKING_MODEL")
        void capacityBookable_BR09() {
            jdbc.update("""
                    INSERT INTO bookable (venue_id, bookable_type_id, name, created_at, updated_at)
                    SELECT v.id, t.id, 'Test Booking Hall', now(), now()
                    FROM venue v, bookable_type t
                    WHERE v.name = 'Central Library' AND t.code = 'HALL'
                    """);
            try {
                Response response = hold(memberA, "Test Booking Hall", "10:00", "12:00");
                assertThat(response.status()).isEqualTo(422);
                assertThat(response.code()).isEqualTo("WRONG_BOOKING_MODEL");
            } finally {
                jdbc.update("DELETE FROM bookable WHERE name = 'Test Booking Hall'");
            }
        }

        @Test
        @DisplayName("API-06: an unknown bookable → 404")
        void unknownBookable() {
            Response response = http.post("/api/v1/bookings", memberA,
                    request(999_999L, tomorrowAt("10:00"), tomorrowAt("11:00")));
            assertThat(response.status()).isEqualTo(404);
            assertThat(response.code()).isEqualTo("NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("All booking rules over HTTP (M1.7)")
    class AllRules {

        @Test
        @DisplayName("BR-02: before opening (Sunrise Turf opens 06:00) → 422 OUTSIDE_OPERATING_HOURS")
        void outsideHours_BR02() {
            Response response = hold(memberA, "Court A", "05:00", "06:00");
            assertThat(response.status()).isEqualTo(422);
            assertThat(response.code()).isEqualTo("OUTSIDE_OPERATING_HOURS");
        }

        @Test
        @DisplayName("BR-03: 10:30 is not on Court A's 60-minute grid from 06:00 → 422 OFF_GRID")
        void offGrid_BR03() {
            Response response = hold(memberA, "Court A", "10:30", "11:30");
            assertThat(response.status()).isEqualTo(422);
            assertThat(response.code()).isEqualTo("OFF_GRID");
        }

        @Test
        @DisplayName("BR-04: 3 hours on a court (max 2) → 422 DURATION_INVALID")
        void tooLong_BR04() {
            Response response = hold(memberA, "Court A", "10:00", "13:00");
            assertThat(response.status()).isEqualTo(422);
            assertThat(response.code()).isEqualTo("DURATION_INVALID");
        }

        @Test
        @DisplayName("BR-07: today + 7 local days is beyond the horizon → 422 BEYOND_HORIZON")
        void beyondHorizon_BR07() {
            Instant start = LocalDate.now(KOLKATA).plusDays(7).atTime(10, 0).atZone(KOLKATA).toInstant();
            Response response = http.post("/api/v1/bookings", memberA,
                    request(bookable("Court A"), start, start.plus(Duration.ofHours(1))));
            assertThat(response.status()).isEqualTo(422);
            assertThat(response.code()).isEqualTo("BEYOND_HORIZON");
        }

        @Test
        @DisplayName("BR-08: a 4th active booking in Sports → 422 MEMBER_LIMIT_REACHED; another category still works")
        void memberLimit_BR08() {
            assertThat(hold(memberA, "Court A", "08:00", "09:00").status()).isEqualTo(201);
            assertThat(hold(memberA, "Court A", "10:00", "11:00").status()).isEqualTo(201);
            assertThat(hold(memberA, "Court B", "12:00", "13:00").status()).isEqualTo(201);

            Response fourth = hold(memberA, "Court B", "14:00", "15:00");
            assertThat(fourth.status()).isEqualTo(422);
            assertThat(fourth.code()).isEqualTo("MEMBER_LIMIT_REACHED");
            assertThat(hold(memberA, "Group Room 1", "16:00", "16:30").status()).isEqualTo(201);
        }

        @Test
        @DisplayName("BR-08: holds that ran out don't count toward the limit")
        void runOutHoldsDontCount_BR08() {
            hold(memberA, "Court A", "08:00", "09:00");
            hold(memberA, "Court A", "10:00", "11:00");
            hold(memberA, "Court B", "12:00", "13:00");
            clock.advance(PAST_THE_HOLD);

            assertThat(hold(memberA, "Court B", "14:00", "15:00").status()).isEqualTo(201);
        }

        @Test
        @DisplayName("FR-BK-07: cancellableUntil is the hold's expiry, then start minus the 2-hour cutoff once confirmed")
        void cancellableUntil_FRBK07() {
            Response held = hold(memberA, "Court A", "10:00", "11:00");
            assertThat(held.body().get("cancellableUntil")).isEqualTo(held.body().get("expiresAt"));

            Response confirmed = confirm(memberA, id(held));
            assertThat(Instant.parse(confirmed.body().get("cancellableUntil").asString()))
                    .isEqualTo(tomorrowAt("10:00").minus(Duration.ofHours(2)));
        }
    }

    @Nested
    @DisplayName("Confirm")
    class Confirm {

        @Test
        @DisplayName("FR-BK-03 / BR-13: HELD → CONFIRMED; confirming again → 409 INVALID_STATE")
        void confirmOnce_FRBK03() {
            long id = id(hold(memberA, "Court A", "10:00", "11:00"));

            Response confirmed = confirm(memberA, id);
            assertThat(confirmed.status()).isEqualTo(200);
            assertThat(confirmed.body().get("status").asString()).isEqualTo("CONFIRMED");
            assertThat(confirmed.body().get("expiresAt").isNull()).isTrue();

            Response again = confirm(memberA, id);
            assertThat(again.status()).isEqualTo(409);
            assertThat(again.code()).isEqualTo("INVALID_STATE");
            assertThat(status(id)).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("FS-08: confirm after the hold ran out (job not run) → 409 HOLD_EXPIRED, row saved as EXPIRED")
        void confirmAfterExpiry_FS08() {
            long id = id(hold(memberA, "Court A", "10:00", "11:00"));
            clock.advance(PAST_THE_HOLD);

            Response response = confirm(memberA, id);
            assertThat(response.status()).isEqualTo(409);
            assertThat(response.code()).isEqualTo("HOLD_EXPIRED");
            // Saved despite the 409: the transaction does not roll back on this refusal.
            assertThat(status(id)).isEqualTo("EXPIRED");
            assertThat(timestamp("expires_at", id)).isNull();
        }

        @Test
        @DisplayName("FR-ROLE-04: even an admin cannot confirm someone else's hold (owner only) → 404")
        void onlyOwnerConfirms() {
            long id = id(hold(memberA, "Court A", "10:00", "11:00"));
            assertThat(confirm(admin, id).status()).isEqualTo(404);
            assertThat(status(id)).isEqualTo("HELD");
        }
    }

    @Nested
    @DisplayName("Cancel")
    class Cancel {

        @Test
        @DisplayName("FR-BK-04: the owner cancels a hold with a reason; cancelling again → 409 INVALID_STATE")
        void memberCancels_FRBK04() {
            long id = id(hold(memberA, "Court A", "10:00", "11:00"));

            Response cancelled = cancel(memberA, id, "Plans changed");
            assertThat(cancelled.status()).isEqualTo(200);
            assertThat(cancelled.body().get("status").asString()).isEqualTo("CANCELLED");
            assertThat(cancelled.body().get("cancelledBy").asString()).isEqualTo("MEMBER");
            assertThat(cancelled.body().get("cancelledAt").isNull()).isFalse();
            assertThat(jdbc.queryForObject("SELECT cancel_reason FROM booking WHERE id = ?", String.class, id))
                    .isEqualTo("Plans changed");

            Response again = cancel(memberA, id, null);
            assertThat(again.status()).isEqualTo(409);
            assertThat(again.code()).isEqualTo("INVALID_STATE");
        }

        @Test
        @DisplayName("CXL-03 / CXL-05: an admin cancels a member's confirmed booking; the slot is free at once")
        void adminCancels_CXL03() {
            long id = id(hold(memberA, "Court A", "10:00", "11:00"));
            confirm(memberA, id);

            Response cancelled = cancel(admin, id, null);
            assertThat(cancelled.status()).isEqualTo(200);
            assertThat(cancelled.body().get("cancelledBy").asString()).isEqualTo("ADMIN");

            assertThat(hold(memberB, "Court A", "10:00", "11:00").status()).isEqualTo(201);
        }

        @Test
        @DisplayName("A run-out hold cannot be cancelled → 409 INVALID_STATE; it is saved as EXPIRED")
        void cancelAfterExpiry() {
            long id = id(hold(memberA, "Court A", "10:00", "11:00"));
            clock.advance(PAST_THE_HOLD);

            Response response = cancel(memberA, id, null);
            assertThat(response.status()).isEqualTo(409);
            assertThat(response.code()).isEqualTo("INVALID_STATE");
            assertThat(status(id)).isEqualTo("EXPIRED");
        }

        @Test
        @DisplayName("A reason over 200 characters → 400 VALIDATION_FAILED")
        void reasonTooLong() {
            long id = id(hold(memberA, "Court A", "10:00", "11:00"));
            Response response = cancel(memberA, id, "x".repeat(201));
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
            assertThat(status(id)).isEqualTo("HELD");
        }
    }

    @Nested
    @DisplayName("Reading bookings")
    class Reading {

        @Test
        @DisplayName("FS-15 / FR-ROLE-04: another member's booking is 404 for read, confirm and cancel")
        void othersBookingNotFound_FS15() {
            long id = id(hold(memberA, "Court A", "10:00", "11:00"));

            Response read = http.get("/api/v1/bookings/" + id, memberB);
            assertThat(read.status()).isEqualTo(404);
            assertThat(read.code()).isEqualTo("NOT_FOUND");
            assertThat(confirm(memberB, id).status()).isEqualTo(404);
            assertThat(cancel(memberB, id, null).status()).isEqualTo(404);
            assertThat(status(id)).isEqualTo("HELD");
        }

        @Test
        @DisplayName("FR-BK-06: the owner and an admin can read a booking")
        void ownerAndAdminRead_FRBK06() {
            long id = id(hold(memberA, "Court A", "10:00", "11:00"));

            assertThat(http.get("/api/v1/bookings/" + id, memberA).status()).isEqualTo(200);
            Response asAdmin = http.get("/api/v1/bookings/" + id, admin);
            assertThat(asAdmin.status()).isEqualTo(200);
            assertThat(asAdmin.body().get("bookableName").asString()).isEqualTo("Court A");
        }

        @Test
        @DisplayName("FR-BK-05: my bookings, newest start first, paged, filtered by status and start time")
        void myBookings_FRBK05() {
            long early = id(hold(memberA, "Court A", "08:00", "09:00"));
            long middle = id(hold(memberA, "Court A", "10:00", "11:00"));
            long late = id(hold(memberA, "Court B", "12:00", "13:00"));
            confirm(memberA, middle);
            hold(memberB, "Court B", "08:00", "09:00");

            Response firstPage = http.get("/api/v1/bookings/me?size=2", memberA);
            assertThat(firstPage.status()).isEqualTo(200);
            assertThat(ids(firstPage)).containsExactly(late, middle);
            assertThat(firstPage.body().get("totalItems").asLong()).isEqualTo(3);
            assertThat(firstPage.body().get("totalPages").asInt()).isEqualTo(2);
            assertThat(ids(http.get("/api/v1/bookings/me?size=2&page=1", memberA))).containsExactly(early);

            assertThat(ids(http.get("/api/v1/bookings/me?status=CONFIRMED", memberA))).containsExactly(middle);
            assertThat(ids(http.get("/api/v1/bookings/me?from=" + tomorrowAt("09:30") + "&to=" + tomorrowAt("12:00"),
                    memberA))).containsExactly(middle);
        }

        @Test
        @DisplayName("FR-BK-05: several statuses at once, and soonest first with order=asc (the Upcoming tab)")
        void myBookingsStatusesAndOrder_FRBK05() {
            long early = id(hold(memberA, "Court A", "08:00", "09:00"));
            long middle = id(hold(memberA, "Court A", "10:00", "11:00"));
            long late = id(hold(memberA, "Court B", "12:00", "13:00"));
            confirm(memberA, middle);
            http.post("/api/v1/bookings/" + late + "/cancel", memberA, null);

            assertThat(ids(http.get("/api/v1/bookings/me?status=HELD&status=CONFIRMED&order=asc", memberA)))
                    .containsExactly(early, middle);
            assertThat(ids(http.get("/api/v1/bookings/me?status=CANCELLED&status=EXPIRED", memberA))).containsExactly(late);
            Response badOrder = http.get("/api/v1/bookings/me?order=newest", memberA);
            assertThat(badOrder.status()).isEqualTo(400);
            assertThat(badOrder.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("order");
        }

        @Test
        @DisplayName("FR-BK-05: an unknown status → 400 VALIDATION_FAILED")
        void badStatusFilter() {
            Response response = http.get("/api/v1/bookings/me?status=MAYBE", memberA);
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
        }

        private List<Long> ids(Response page) {
            return page.body().get("items").valueStream().map(item -> item.get("id").asLong()).toList();
        }
    }
}
