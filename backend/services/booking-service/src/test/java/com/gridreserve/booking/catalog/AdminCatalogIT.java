package com.gridreserve.booking.catalog;

import com.gridreserve.booking.concurrency.Parallel;
import com.gridreserve.booking.support.Http;
import com.gridreserve.booking.support.Http.Response;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestCatalog;
import com.gridreserve.booking.support.TestJwtKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.gridreserve.booking.support.TestCatalog.PREFIX;
import static com.gridreserve.booking.support.TestCatalog.bookableBody;
import static com.gridreserve.booking.support.TestCatalog.venueBody;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin catalog management through HTTP (FR-CAT-07..16, US-09): validation, roles, the soft close (FS-13),
 * venue flags, optimistic versions and rule changes. Every venue and bookable here is created by the test
 * and removed afterwards (TestCatalog), so the seeded catalog other tests count stays as it is.
 */
@ServiceIntegrationTest
class AdminCatalogIT {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final long FIRST_MEMBER = 960_000L;

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    Http http;
    TestCatalog catalog;
    String admin;

    @BeforeEach
    void setUp() {
        http = new Http(port);
        catalog = new TestCatalog(http, jdbc);
        admin = TestJwtKeys.adminToken();
    }

    @AfterEach
    void cleanUp() {
        catalog.removeAll();
    }

    private static Instant tomorrowAt(String localTime) {
        return LocalDate.now(KOLKATA).plusDays(1).atTime(LocalTime.parse(localTime)).atZone(KOLKATA).toInstant();
    }

    private Response hold(long member, long bookableId, String from, String to) {
        return http.post("/api/v1/bookings", TestJwtKeys.memberToken(member), Map.of("bookableId", bookableId,
                "start", tomorrowAt(from).toString(), "end", tomorrowAt(to).toString()));
    }

    private static List<String> fieldNames(Response response) {
        List<String> fields = new ArrayList<>();
        response.body().get("fieldErrors").forEach(error -> fields.add(error.get("field").asString()));
        return fields;
    }

    private static Map<String, Object> with(Map<String, Object> body, String key, Object value) {
        Map<String, Object> copy = new HashMap<>(body);
        copy.put(key, value);
        return copy;
    }

    // ---- create and read ----

    @Test
    @DisplayName("FR-CAT-07/08: an admin creates a venue and a bookable; members see them with the effective rules")
    void createVenueAndBookable_FRCAT07() {
        Response venue = http.post("/api/v1/venues", admin, venueBody(catalog.categoryId("SPORTS"), "Arena"));
        assertThat(venue.status()).isEqualTo(201);
        long venueId = venue.body().get("id").asLong();
        assertThat(venue.location()).isEqualTo("/api/v1/venues/" + venueId);
        assertThat(venue.body().get("version").asLong()).isZero();
        assertThat(venue.body().get("city").asString()).isEqualTo("Testville");
        assertThat(venue.body().get("openingHours")).hasSize(7);
        assertThat(venue.body().get("openingHours").get(0).get("closesAt").asString()).isEqualTo("24:00");

        Map<String, Object> court = with(bookableBody(catalog.typeId("COURT"), "Arena Court"), "maxDurationMinutes", 180);
        Response bookable = http.post("/api/v1/venues/" + venueId + "/bookables", admin, court);
        assertThat(bookable.status()).isEqualTo(201);
        JsonNode body = bookable.body();
        assertThat(body.get("label").asString()).isEqualTo("Court");
        assertThat(body.get("rules").get("slotMinutes").asInt()).isEqualTo(60);            // the type's default
        assertThat(body.get("rules").get("maxDurationMinutes").asInt()).isEqualTo(180);     // the override
        assertThat(body.get("overrides").get("maxDurationMinutes").asInt()).isEqualTo(180);
        assertThat(body.get("overrides").get("slotMinutes").isNull()).isTrue();
        assertThat(body.get("ownOpeningHours").asBoolean()).isFalse();

        Response seen = http.get("/api/v1/bookables/" + body.get("id").asLong(), TestJwtKeys.memberToken(FIRST_MEMBER));
        assertThat(seen.status()).isEqualTo(200);
        assertThat(seen.body().get("name").asString()).isEqualTo(PREFIX + "Arena Court");
    }

    @Test
    @DisplayName("FR-CAT-04: a bookable's own hours replace the venue's; sending none returns it to the venue's")
    void ownHours_FRCAT04() {
        long venueId = catalog.venue("SPORTS", "Hours Venue");
        Map<String, Object> body = with(bookableBody(catalog.typeId("COURT"), "Hours Court"), "openingHours",
                List.of(Map.of("weekday", "SATURDAY", "opensAt", "07:00", "closesAt", "12:00")));
        Response created = http.post("/api/v1/venues/" + venueId + "/bookables", admin, body);
        assertThat(created.body().get("ownOpeningHours").asBoolean()).isTrue();
        assertThat(created.body().get("openingHours")).hasSize(1);

        long id = created.body().get("id").asLong();
        Map<String, Object> update = with(with(bookableBody(catalog.typeId("COURT"), "Hours Court"), "version",
                created.body().get("version").asLong()), "openingHours", null);
        Response updated = http.put("/api/v1/bookables/" + id, admin, update);
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body().get("ownOpeningHours").asBoolean()).isFalse();
        assertThat(updated.body().get("openingHours")).hasSize(7);
    }

    // ---- validation and roles ----

    @Test
    @DisplayName("FR-CAT-09/14: every invalid field is reported at once, with 400 VALIDATION_FAILED")
    void venueValidation_FRCAT09() {
        Map<String, Object> body = venueBody(catalog.categoryId("SPORTS"), "Invalid");
        body = with(body, "timeZone", "Europe/London");                          // not a zone of IN
        body = with(body, "openingHours", List.of(
                Map.of("weekday", "MONDAY", "opensAt", "10:00", "closesAt", "09:00"),   // closes before it opens
                Map.of("weekday", "MONDAY", "opensAt", "11:00", "closesAt", "12:00"))); // Monday twice

        Response response = http.post("/api/v1/venues", admin, body);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(fieldNames(response)).containsExactlyInAnyOrder("timeZone", "openingHours[0]", "openingHours[1].weekday");
    }

    @Test
    @DisplayName("FR-CAT-09: Bean Validation catches missing and malformed fields (name, country, time format)")
    void venueFieldFormats_FRCAT09() {
        Map<String, Object> body = with(with(venueBody(catalog.categoryId("SPORTS"), "x"), "name", ""), "countryCode", "IND");
        body = with(body, "openingHours", List.of(Map.of("weekday", "MONDAY", "opensAt", "24:00", "closesAt", "24:00")));

        body = with(body, "city", " ");

        Response response = http.post("/api/v1/venues", admin, body);

        assertThat(response.status()).isEqualTo(400);
        assertThat(fieldNames(response)).contains("name", "city", "countryCode", "openingHours[0].opensAt");
    }

    @Test
    @DisplayName("FR-CAT-08/09: the type must be of the venue's category; rules are checked against the type's defaults")
    void bookableValidation_FRCAT08() {
        long venueId = catalog.venue("SPORTS", "Rules Venue");

        Response otherCategory = http.post("/api/v1/venues/" + venueId + "/bookables", admin,
                bookableBody(catalog.typeId("ROOM"), "Room at a turf"));
        assertThat(otherCategory.status()).isEqualTo(400);
        assertThat(fieldNames(otherCategory)).containsExactly("bookableTypeId");

        // Court: 60 / 60 / 120. A 30-minute slot is fine, a 90-minute maximum isn't a whole number of 60s.
        Map<String, Object> rules = with(with(bookableBody(catalog.typeId("COURT"), "Bad court"), "slotMinutes", 45),
                "bufferMinutes", 500);
        Response broken = http.post("/api/v1/venues/" + venueId + "/bookables", admin, rules);
        assertThat(broken.status()).isEqualTo(400);
        assertThat(fieldNames(broken)).containsExactlyInAnyOrder("bufferMinutes");   // Bean Validation answers first

        Response notMultiple = http.post("/api/v1/venues/" + venueId + "/bookables", admin,
                with(bookableBody(catalog.typeId("COURT"), "Odd court"), "maxDurationMinutes", 90));
        assertThat(fieldNames(notMultiple)).containsExactly("maxDurationMinutes");
    }

    @Test
    @DisplayName("BR-09: a CAPACITY type can't be added in Phase 1 → 422 WRONG_BOOKING_MODEL")
    void capacityTypeRefused_BR09() {
        long venueId = catalog.venue("STUDY", "Hall Venue");

        Response response = http.post("/api/v1/venues/" + venueId + "/bookables", admin,
                bookableBody(catalog.typeId("HALL"), "Reading hall"));

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.code()).isEqualTo("WRONG_BOOKING_MODEL");
    }

    @Test
    @DisplayName("FR-ROLE-02: members can't create, change, open or close anything (403); unknown IDs 404")
    void adminOnly_FRROLE02() {
        long venueId = catalog.venue("SPORTS", "Role Venue");
        long courtId = catalog.bookable(venueId, "COURT", "Role Court");
        String member = TestJwtKeys.memberToken(FIRST_MEMBER);

        assertThat(http.post("/api/v1/venues", member, venueBody(catalog.categoryId("SPORTS"), "No")).status()).isEqualTo(403);
        assertThat(http.post("/api/v1/venues/" + venueId + "/bookables", member,
                bookableBody(catalog.typeId("COURT"), "No")).status()).isEqualTo(403);
        assertThat(http.post("/api/v1/bookables/" + courtId + "/deactivate", member, null).status()).isEqualTo(403);
        assertThat(http.post("/api/v1/venues/" + venueId + "/deactivate", member, null).status()).isEqualTo(403);
        assertThat(http.put("/api/v1/bookables/" + courtId, member, Map.of()).status()).isEqualTo(403);
        assertThat(http.post("/api/v1/bookables/99999999/deactivate", admin, null).status()).isEqualTo(404);
        assertThat(http.put("/api/v1/venues/99999999", admin, with(venueBody(catalog.categoryId("SPORTS"), "x"), "version", 0))
                .status()).isEqualTo(404);
    }

    // ---- soft close ----

    @Test
    @DisplayName("FS-13: closing a bookable with a confirmed booking and an open hold → 200; both stay valid; new holds 422; hidden from members")
    void softClose_FS13() {
        long venueId = catalog.venue("SPORTS", "Close Venue");
        long courtId = catalog.bookable(venueId, "COURT", "Close Court");
        Response confirmed = hold(FIRST_MEMBER, courtId, "18:00", "19:00");
        http.post("/api/v1/bookings/" + confirmed.body().get("id").asLong() + "/confirm",
                TestJwtKeys.memberToken(FIRST_MEMBER), null);
        Response openHold = hold(FIRST_MEMBER + 1, courtId, "20:00", "21:00");

        Response closed = http.post("/api/v1/bookables/" + courtId + "/deactivate", admin, null);

        assertThat(closed.status()).isEqualTo(200);
        assertThat(closed.body().get("active").asBoolean()).isFalse();
        assertThat(closed.body().get("activeFutureBookings").asLong()).isEqualTo(2);
        // The open hold can still be confirmed, and the confirmed booking is untouched (FR-CAT-10).
        Response confirmLater = http.post("/api/v1/bookings/" + openHold.body().get("id").asLong() + "/confirm",
                TestJwtKeys.memberToken(FIRST_MEMBER + 1), null);
        assertThat(confirmLater.status()).isEqualTo(200);
        assertThat(http.get("/api/v1/bookings/" + confirmed.body().get("id").asLong(), TestJwtKeys.memberToken(FIRST_MEMBER))
                .body().get("status").asString()).isEqualTo("CONFIRMED");
        // No new holds; members no longer see it; admins do.
        Response refused = hold(FIRST_MEMBER + 2, courtId, "10:00", "11:00");
        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("BOOKABLE_INACTIVE");
        assertThat(http.get("/api/v1/bookables/" + courtId, TestJwtKeys.memberToken(FIRST_MEMBER)).status()).isEqualTo(404);
        assertThat(http.get("/api/v1/bookables/" + courtId + "/availability?date=" + LocalDate.now(KOLKATA).plusDays(1),
                TestJwtKeys.memberToken(FIRST_MEMBER)).status()).isEqualTo(404);
        assertThat(http.get("/api/v1/venues/" + venueId + "/bookables", TestJwtKeys.memberToken(FIRST_MEMBER))
                .body().get("items")).isEmpty();
        assertThat(http.get("/api/v1/bookables/" + courtId, admin).body().get("active").asBoolean()).isFalse();
        // Closing again is fine (200), and reopening takes new holds.
        assertThat(http.post("/api/v1/bookables/" + courtId + "/deactivate", admin, null).status()).isEqualTo(200);
        assertThat(http.post("/api/v1/bookables/" + courtId + "/activate", admin, null).body().get("active").asBoolean()).isTrue();
        assertThat(hold(FIRST_MEMBER + 2, courtId, "10:00", "11:00").status()).isEqualTo(201);
    }

    @Test
    @DisplayName("FR-CAT-13: closing a venue stops holds on its bookables; reopening restores exactly the bookables that were open")
    void venueSoftClose_FRCAT13() {
        long venueId = catalog.venue("SPORTS", "Venue Close");
        long open = catalog.bookable(venueId, "COURT", "Open Court");
        long closed = catalog.bookable(venueId, "COURT", "Closed Court");
        http.post("/api/v1/bookables/" + closed + "/deactivate", admin, null);
        hold(FIRST_MEMBER, open, "18:00", "19:00");

        Response venueClosed = http.post("/api/v1/venues/" + venueId + "/deactivate", admin, null);
        assertThat(venueClosed.body().get("activeFutureBookings").asLong()).isEqualTo(1);
        assertThat(hold(FIRST_MEMBER + 1, open, "10:00", "11:00").code()).isEqualTo("BOOKABLE_INACTIVE");
        assertThat(http.get("/api/v1/venues/" + venueId, TestJwtKeys.memberToken(FIRST_MEMBER)).status()).isEqualTo(404);

        http.post("/api/v1/venues/" + venueId + "/activate", admin, null);
        assertThat(hold(FIRST_MEMBER + 1, open, "10:00", "11:00").status()).isEqualTo(201);
        assertThat(hold(FIRST_MEMBER + 2, closed, "10:00", "11:00").code()).isEqualTo("BOOKABLE_INACTIVE");
        assertThat(http.get("/api/v1/bookables/" + closed, admin).body().get("active").asBoolean()).isFalse();
    }

    // ---- edits ----

    @Test
    @DisplayName("FR-CAT-15: saving with the version read → 200 and a new version; saving that old version again → 409 STALE_UPDATE")
    void staleVersion_FRCAT15() {
        long venueId = catalog.venue("SPORTS", "Version Venue");
        long version = http.get("/api/v1/venues/" + venueId, admin).body().get("version").asLong();
        Map<String, Object> edit = with(with(venueBody(0, "Version Venue renamed"), "version", version), "categoryId", null);

        Response first = http.put("/api/v1/venues/" + venueId, admin, edit);
        Response second = http.put("/api/v1/venues/" + venueId, admin, edit);

        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().get("version").asLong()).isEqualTo(version + 1);
        assertThat(first.body().get("name").asString()).isEqualTo(PREFIX + "Version Venue renamed");
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.code()).isEqualTo("STALE_UPDATE");
    }

    @RepeatedTest(20)
    @DisplayName("FR-CAT-15: two admins save the same version at the same moment → exactly one 200 and one 409 STALE_UPDATE")
    void concurrentEdits_FRCAT15() throws Exception {
        long venueId = catalog.venue("SPORTS", "Race Venue");
        long courtId = catalog.bookable(venueId, "COURT", "Race Court");
        long version = http.get("/api/v1/bookables/" + courtId, admin).body().get("version").asLong();

        List<Integer> statuses = Parallel.run(2, index -> () -> http.put("/api/v1/bookables/" + courtId, admin,
                with(with(bookableBody(catalog.typeId("COURT"), "Race Court " + index), "version", version),
                        "maxDurationMinutes", 60 * (index + 1))).status());

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT version FROM bookable WHERE id = ?", Long.class, courtId))
                .isEqualTo(version + 1);
    }

    @Test
    @DisplayName("FR-CAT-12: a rule change applies to new bookings only; an existing 2-hour booking keeps its times")
    void ruleChangeKeepsBookings_FRCAT12() {
        long venueId = catalog.venue("SPORTS", "Rule Venue");
        long courtId = catalog.bookable(venueId, "COURT", "Rule Court");
        Response twoHours = hold(FIRST_MEMBER, courtId, "18:00", "20:00");
        JsonNode court = http.get("/api/v1/bookables/" + courtId, admin).body();

        Response changed = http.put("/api/v1/bookables/" + courtId, admin, with(with(bookableBody(catalog.typeId("COURT"),
                "Rule Court"), "version", court.get("version").asLong()), "maxDurationMinutes", 60));

        assertThat(changed.status()).isEqualTo(200);
        assertThat(changed.body().get("rules").get("maxDurationMinutes").asInt()).isEqualTo(60);
        JsonNode existing = http.get("/api/v1/bookings/" + twoHours.body().get("id").asLong(),
                TestJwtKeys.memberToken(FIRST_MEMBER)).body();
        assertThat(existing.get("start").asString()).isEqualTo(twoHours.body().get("start").asString());
        assertThat(existing.get("end").asString()).isEqualTo(twoHours.body().get("end").asString());
        assertThat(existing.get("status").asString()).isEqualTo("HELD");
        assertThat(hold(FIRST_MEMBER + 1, courtId, "10:00", "12:00").code()).isEqualTo("DURATION_INVALID");
    }
}
