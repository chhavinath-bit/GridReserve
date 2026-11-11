package com.gridreserve.booking.schema;

import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestBookings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static com.gridreserve.booking.support.SqlErrors.CHECK_VIOLATION;
import static com.gridreserve.booking.support.SqlErrors.EXCLUSION_VIOLATION;
import static com.gridreserve.booking.support.SqlErrors.FOREIGN_KEY_VIOLATION;
import static com.gridreserve.booking.support.SqlErrors.assertRejectedBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Proves the booking guarantees in the database itself, with plain SQL and no Java rules involved:
 * if application code had a bug, these constraints would still refuse the write.
 * Times are fixed UTC instants; the constraints do not depend on "now".
 */
@ServiceIntegrationTest
class BookingConstraintsIT {

    private static final long ALICE = 9001;
    private static final long BOB = 9002;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        TestBookings.delete(jdbc, "true");
        jdbc.update("DELETE FROM member");
    }

    // --- helpers ---------------------------------------------------------------------------------

    private long bookableId(String name) {
        return jdbc.queryForObject("SELECT id FROM bookable WHERE name = ?", Long.class, name);
    }

    private void member(long id) {
        jdbc.update("INSERT INTO member (id, created_at) VALUES (?, now()) ON CONFLICT (id) DO NOTHING", id);
    }

    private static Timestamp at(String utc) {
        return Timestamp.from(Instant.parse(utc));
    }

    /** Inserts a booking; occupiedUntil = end + buffer, as the service will compute it. */
    private void book(long memberId, String bookable, String start, String end, String occupiedUntil, String status) {
        member(memberId);
        Timestamp expiresAt = status.equals("HELD") ? at("2030-01-01T00:00:00Z") : null;
        jdbc.update("""
                INSERT INTO booking (member_id, bookable_id, start_time, end_time, occupied_until, status,
                                     expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
                """, memberId, bookableId(bookable), at(start), at(end), at(occupiedUntil), status, expiresAt);
    }

    private void book(long memberId, String bookable, String start, String end, String status) {
        book(memberId, bookable, start, end, end, status);
    }

    // --- bookable overlap -------------------------------------------------------------------------

    @Nested
    @DisplayName("One bookable, one active booking at a time")
    class BookableOverlap {

        @Test
        @DisplayName("BR-10: the same range on the same bookable is rejected by ex_booking_bookable_overlap")
        void sameRangeRejected_BR10() {
            book(ALICE, "Group Room 1", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "HELD");

            assertRejectedBy(EXCLUSION_VIOLATION, "ex_booking_bookable_overlap", () ->
                    book(BOB, "Group Room 1", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "HELD"));
        }

        @Test
        @DisplayName("BR-10: a partly overlapping range is rejected, also against a CONFIRMED booking")
        void partialOverlapRejected_BR10() {
            book(ALICE, "Group Room 1", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "CONFIRMED");

            assertRejectedBy(EXCLUSION_VIOLATION, "ex_booking_bookable_overlap", () ->
                    book(BOB, "Group Room 1", "2030-01-07T05:00:00Z", "2030-01-07T06:00:00Z", "HELD"));
        }

        @Test
        @DisplayName("BR-12: back-to-back bookings are allowed because ranges are half-open [start, end)")
        void backToBackAllowed_BR12() {
            book(ALICE, "Group Room 1", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "CONFIRMED");

            assertThatCode(() -> book(BOB, "Group Room 1", "2030-01-07T05:30:00Z", "2030-01-07T06:30:00Z", "HELD"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("BR-10: CANCELLED and EXPIRED bookings do not block the range")
        void inactiveBookingsDoNotBlock_BR10() {
            book(ALICE, "Group Room 1", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "CANCELLED");
            book(ALICE, "Group Room 1", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "EXPIRED");

            assertThatCode(() -> book(BOB, "Group Room 1", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "HELD"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("BR-10: different bookables at the same time do not conflict")
        void differentBookablesDoNotConflict_BR10() {
            book(ALICE, "Group Room 1", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "HELD");

            assertThatCode(() -> book(BOB, "Group Room 2", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "HELD"))
                    .doesNotThrowAnyException();
        }
    }

    // --- buffer -----------------------------------------------------------------------------------

    @Nested
    @DisplayName("Buffer after a booking (occupied_until)")
    class Buffer {

        // Trainer session 10:00-10:45 London time (UTC in January) + 15-minute buffer → occupied until 11:00.
        private void trainerSessionForAlice() {
            book(ALICE, "Asha (personal training)",
                    "2030-01-07T10:00:00Z", "2030-01-07T10:45:00Z", "2030-01-07T11:00:00Z", "CONFIRMED");
        }

        @Test
        @DisplayName("A-08: a start inside the trainer's 15-minute buffer is rejected")
        void startInsideBufferRejected_A08() {
            trainerSessionForAlice();

            assertRejectedBy(EXCLUSION_VIOLATION, "ex_booking_bookable_overlap", () ->
                    book(BOB, "Asha (personal training)",
                            "2030-01-07T10:45:00Z", "2030-01-07T11:30:00Z", "2030-01-07T11:45:00Z", "HELD"));
        }

        @Test
        @DisplayName("A-08: the next session may start exactly when the buffer ends")
        void startAfterBufferAllowed_A08() {
            trainerSessionForAlice();

            assertThatCode(() -> book(BOB, "Asha (personal training)",
                    "2030-01-07T11:00:00Z", "2030-01-07T11:45:00Z", "2030-01-07T12:00:00Z", "HELD"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("BR-11: the buffer belongs to the bookable, so the member may book elsewhere right after the session")
        void bufferDoesNotBlockTheMember_BR11() {
            trainerSessionForAlice();

            assertThatCode(() -> book(ALICE, "Tom (personal training)",
                    "2030-01-07T10:45:00Z", "2030-01-07T11:30:00Z", "2030-01-07T11:45:00Z", "HELD"))
                    .doesNotThrowAnyException();
        }
    }

    // --- member overlap ---------------------------------------------------------------------------

    @Nested
    @DisplayName("One member, one active booking at a time")
    class MemberOverlap {

        @Test
        @DisplayName("BR-11: a seat and a room at the same time for the same member are rejected by ex_booking_member_overlap")
        void seatAndRoomSameTimeRejected_BR11() {
            book(ALICE, "Seat Q1", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "CONFIRMED");

            assertRejectedBy(EXCLUSION_VIOLATION, "ex_booking_member_overlap", () ->
                    book(ALICE, "Group Room 3", "2030-01-07T05:00:00Z", "2030-01-07T05:30:00Z", "HELD"));
        }

        @Test
        @DisplayName("BR-11: the rule applies across categories (study room and sports court)")
        void acrossCategoriesRejected_BR11() {
            book(ALICE, "Group Room 1", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "HELD");

            assertRejectedBy(EXCLUSION_VIOLATION, "ex_booking_member_overlap", () ->
                    book(ALICE, "Court A", "2030-01-07T05:00:00Z", "2030-01-07T06:00:00Z", "HELD"));
        }

        @Test
        @DisplayName("BR-11: a cancelled booking does not block the member")
        void cancelledDoesNotBlockMember_BR11() {
            book(ALICE, "Group Room 1", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "CANCELLED");

            assertThatCode(() -> book(ALICE, "Court A", "2030-01-07T04:30:00Z", "2030-01-07T05:30:00Z", "HELD"))
                    .doesNotThrowAnyException();
        }
    }

    // --- row checks -------------------------------------------------------------------------------

    @Nested
    @DisplayName("Row-level checks")
    class RowChecks {

        @Test
        @DisplayName("Lifecycle: a HELD booking without expires_at is rejected by ck_booking_held_expires")
        void heldNeedsExpiry() {
            member(ALICE);

            assertRejectedBy(CHECK_VIOLATION, "ck_booking_held_expires", () -> jdbc.update("""
                    INSERT INTO booking (member_id, bookable_id, start_time, end_time, occupied_until, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'HELD', now(), now())
                    """, ALICE, bookableId("Group Room 1"),
                    at("2030-01-07T04:30:00Z"), at("2030-01-07T05:30:00Z"), at("2030-01-07T05:30:00Z")));
        }

        @Test
        @DisplayName("Lifecycle: a CONFIRMED booking with expires_at is rejected by ck_booking_held_expires")
        void confirmedHasNoExpiry() {
            member(ALICE);

            assertRejectedBy(CHECK_VIOLATION, "ck_booking_held_expires", () -> jdbc.update("""
                    INSERT INTO booking (member_id, bookable_id, start_time, end_time, occupied_until, status, expires_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'CONFIRMED', ?, now(), now())
                    """, ALICE, bookableId("Group Room 1"), at("2030-01-07T04:30:00Z"), at("2030-01-07T05:30:00Z"),
                    at("2030-01-07T05:30:00Z"), at("2030-01-07T04:35:00Z")));
        }

        @Test
        @DisplayName("Lifecycle: end before start is rejected by ck_booking_time_order")
        void endBeforeStartRejected() {
            assertRejectedBy(CHECK_VIOLATION, "ck_booking_time_order", () ->
                    book(ALICE, "Group Room 1", "2030-01-07T05:30:00Z", "2030-01-07T04:30:00Z", "CONFIRMED"));
        }

        @Test
        @DisplayName("FR-CAT-03: a venue whose zone belongs to another country is rejected by fk_venue_country_zone")
        void zoneMustBelongToCountry() {
            assertRejectedBy(FOREIGN_KEY_VIOLATION, "fk_venue_country_zone", () -> jdbc.update("""
                    INSERT INTO venue (category_id, name, city, country_code, time_zone, created_at, updated_at)
                    SELECT id, 'Wrong Zone Venue', 'Pune', 'IN', 'Europe/London', now(), now() FROM category WHERE code = 'STUDY'
                    """));
        }

        @Test
        @DisplayName("FR-CAT-09: opening hours with opens >= closes are rejected by ck_venue_opening_hours_order")
        void openingHoursOrder() {
            assertRejectedBy(CHECK_VIOLATION, "ck_venue_opening_hours_order", () -> jdbc.update("""
                    UPDATE venue_opening_hours SET opens_at_minute = 1320, closes_at_minute = 480
                    WHERE venue_id = (SELECT id FROM venue WHERE name = 'Central Library') AND iso_weekday = 1
                    """));
        }

        @Test
        @DisplayName("FR-CAT-09: a weekday outside ISO 1-7 is rejected by ck_bookable_opening_hours_weekday")
        void weekdayRange() {
            assertRejectedBy(CHECK_VIOLATION, "ck_bookable_opening_hours_weekday", () -> jdbc.update("""
                    INSERT INTO bookable_opening_hours (bookable_id, iso_weekday, opens_at_minute, closes_at_minute)
                    VALUES (?, 8, 600, 840)
                    """, bookableId("Dr. Rao")));
        }

        @Test
        @DisplayName("FR-CAT-09: a bookable's slot override must be 15, 30 or 60 (ck_bookable_slot)")
        void bookableSlotOverride() {
            assertRejectedBy(CHECK_VIOLATION, "ck_bookable_slot", () ->
                    jdbc.update("UPDATE bookable SET slot_minutes = 20 WHERE name = 'Court A'"));
        }

        @Test
        @DisplayName("FR-CAT-09: a bookable's min-duration override may not exceed its max (ck_bookable_duration_order)")
        void bookableDurationOrder() {
            assertRejectedBy(CHECK_VIOLATION, "ck_bookable_duration_order", () -> jdbc.update(
                    "UPDATE bookable SET min_duration_minutes = 120, max_duration_minutes = 60 WHERE name = 'Court A'"));
        }
    }
}
