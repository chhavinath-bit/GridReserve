package com.gridreserve.booking.catalog;

import com.gridreserve.booking.support.Http;
import com.gridreserve.booking.support.Http.Response;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestJwtKeys;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Catalog reads over real HTTP against the seeded PostgreSQL catalog (FR-CAT-01..06, FR-CAT-14). */
@ServiceIntegrationTest
class CatalogIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    Http http;
    String member;
    String admin;

    @BeforeEach
    void setUp() {
        http = new Http(port);
        member = TestJwtKeys.memberToken();
        admin = TestJwtKeys.adminToken();
    }

    private long id(String table, String name) {
        return jdbc.queryForObject("SELECT id FROM " + table + " WHERE name = ?", Long.class, name);
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.get(field).asString()));
        return values;
    }

    @Nested
    @DisplayName("GET /api/v1/categories")
    class Categories {

        @Test
        @DisplayName("FR-CAT-01/02: five categories, each with its types, labels and default rules")
        void categoriesWithTypes_FRCAT01() {
            Response response = http.get("/api/v1/categories", member);

            assertThat(response.status()).isEqualTo(200);
            assertThat(texts(response.body(), "code")).containsExactly("STUDY", "SPORTS", "PARKING", "CLINIC", "GYM");
            JsonNode study = response.body().get(0);
            assertThat(study.get("bookingHorizonDays").asInt()).isEqualTo(7);
            assertThat(texts(study.get("bookableTypes"), "label")).containsExactly("Room", "Seat", "Hall");
            JsonNode hall = study.get("bookableTypes").get(2);
            assertThat(hall.get("bookingModel").asString()).isEqualTo("CAPACITY");
            assertThat(hall.get("slotMinutes").isNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("Venues")
    class Venues {

        @Test
        @DisplayName("FR-CAT-06/API-04: venues of one category, as a page")
        void venuesOfCategory_FRCAT06() {
            long study = id("category", "Study");

            Response response = http.get("/api/v1/venues?categoryId=" + study, member);

            assertThat(response.status()).isEqualTo(200);
            assertThat(texts(response.body().get("items"), "name")).containsExactly("Central Library");
            assertThat(response.body().get("page").asInt()).isZero();
            assertThat(response.body().get("size").asInt()).isEqualTo(20);
            assertThat(response.body().get("totalItems").asLong()).isEqualTo(1);
            assertThat(response.body().get("totalPages").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("FR-CAT-03: a venue with its zone and weekly hours; a closed weekday is left out")
        void venueDetail_FRCAT03() {
            Response response = http.get("/api/v1/venues/" + id("venue", "CityCare Clinic"), member);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body().get("timeZone").asString()).isEqualTo("Asia/Kolkata");
            JsonNode hours = response.body().get("openingHours");
            assertThat(texts(hours, "weekday")).containsExactly(
                    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY");
            assertThat(hours.get(0).get("opensAt").asString()).isEqualTo("09:00");
            assertThat(hours.get(0).get("closesAt").asString()).isEqualTo("17:00");
        }

        @Test
        @DisplayName("Open all day shows closesAt 24:00")
        void allDayHours() {
            Response response = http.get("/api/v1/venues/" + id("venue", "Palm Apartments"), member);

            assertThat(response.body().get("openingHours").get(0).get("closesAt").asString()).isEqualTo("24:00");
        }

        @Test
        @DisplayName("FR-CAT-06: an inactive venue is hidden from members, even with includeInactive; admins can see it")
        void inactiveVenueHiddenFromMembers_FRCAT06() {
            long venue = id("venue", "Sunrise Turf");
            long sports = id("category", "Sports");
            jdbc.update("UPDATE venue SET active = false WHERE id = ?", venue);
            try {
                assertThat(http.get("/api/v1/venues/" + venue, member).status()).isEqualTo(404);
                assertThat(http.get("/api/v1/venues/" + venue + "/bookables", member).status()).isEqualTo(404);
                assertThat(http.get("/api/v1/venues?categoryId=" + sports + "&includeInactive=true", member)
                        .body().get("totalItems").asLong()).isZero();

                assertThat(http.get("/api/v1/venues/" + venue, admin).status()).isEqualTo(200);
                assertThat(http.get("/api/v1/venues?categoryId=" + sports + "&includeInactive=true", admin)
                        .body().get("totalItems").asLong()).isEqualTo(1);
                assertThat(http.get("/api/v1/venues?categoryId=" + sports, admin)
                        .body().get("totalItems").asLong()).isZero();
            } finally {
                jdbc.update("UPDATE venue SET active = true WHERE id = ?", venue);
            }
        }

        @Test
        @DisplayName("API-04: size outside 1..100 or a negative page → 400 VALIDATION_FAILED")
        void badPageParameters() {
            for (String query : List.of("size=0", "size=101", "page=-1", "size=abc")) {
                Response response = http.get("/api/v1/venues?" + query, member);
                assertThat(response.status()).as(query).isEqualTo(400);
                assertThat(response.code()).as(query).isEqualTo("VALIDATION_FAILED");
            }
        }

        @Test
        @DisplayName("Unknown venue → 404 NOT_FOUND")
        void unknownVenue() {
            Response response = http.get("/api/v1/venues/999999", member);

            assertThat(response.status()).isEqualTo(404);
            assertThat(response.code()).isEqualTo("NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("Bookables")
    class Bookables {

        @Test
        @DisplayName("FR-CAT-06: a venue's bookables, each labelled by its type, paged")
        void bookablesOfVenue_FRCAT06() {
            long library = id("venue", "Central Library");

            Response firstPage = http.get("/api/v1/venues/" + library + "/bookables?size=5", member);

            assertThat(firstPage.status()).isEqualTo(200);
            assertThat(firstPage.body().get("totalItems").asLong()).isEqualTo(16); // 4 rooms + 12 seats
            assertThat(firstPage.body().get("totalPages").asInt()).isEqualTo(4);
            JsonNode items = firstPage.body().get("items");
            assertThat(texts(items, "name")).startsWith("Group Room 1", "Group Room 2");
            assertThat(items.get(0).get("label").asString()).isEqualTo("Room");
            assertThat(items.get(0).get("bookingModel").asString()).isEqualTo("EXCLUSIVE");

            Response lastPage = http.get("/api/v1/venues/" + library + "/bookables?size=5&page=3", member);
            assertThat(texts(lastPage.body().get("items"), "label")).containsOnly("Seat");
        }

        @Test
        @DisplayName("FR-CAT-05/06: a bookable shows its effective rules, zone and hours")
        void bookableDetail_FRCAT05() {
            Response response = http.get("/api/v1/bookables/" + id("bookable", "Asha (personal training)"), member);

            assertThat(response.status()).isEqualTo(200);
            JsonNode body = response.body();
            assertThat(body.get("label").asString()).isEqualTo("Trainer");
            assertThat(body.get("venueTimeZone").asString()).isEqualTo("Europe/London");
            assertThat(body.get("rules").get("slotMinutes").asInt()).isEqualTo(15);
            assertThat(body.get("rules").get("minDurationMinutes").asInt()).isEqualTo(45);
            assertThat(body.get("rules").get("bufferMinutes").asInt()).isEqualTo(15);
            assertThat(body.get("rules").get("cancellationCutoffMinutes").asInt()).isEqualTo(120);
            assertThat(body.get("ownOpeningHours").asBoolean()).isFalse();
            assertThat(body.get("openingHours")).hasSize(7);
        }

        @Test
        @DisplayName("FR-CAT-04: a bookable's own hours replace the venue's entirely")
        void ownHoursReplaceVenueHours_FRCAT04() {
            Response response = http.get("/api/v1/bookables/" + id("bookable", "Dr. Mehta"), member);

            assertThat(response.body().get("ownOpeningHours").asBoolean()).isTrue();
            JsonNode hours = response.body().get("openingHours");
            assertThat(texts(hours, "weekday")).containsExactly("TUESDAY", "THURSDAY");
            assertThat(hours.get(0).get("opensAt").asString()).isEqualTo("10:00");
            assertThat(hours.get(0).get("closesAt").asString()).isEqualTo("14:00");
        }

        @Test
        @DisplayName("FR-CAT-06/10: an inactive bookable is 404 for members, visible to admins")
        void inactiveBookableHidden_FRCAT10() {
            long bookable = id("bookable", "Court B");
            jdbc.update("UPDATE bookable SET active = false WHERE id = ?", bookable);
            try {
                assertThat(http.get("/api/v1/bookables/" + bookable, member).status()).isEqualTo(404);
                assertThat(http.get("/api/v1/bookables/" + bookable, admin).status()).isEqualTo(200);
                long turf = id("venue", "Sunrise Turf");
                assertThat(http.get("/api/v1/venues/" + turf + "/bookables", member)
                        .body().get("totalItems").asLong()).isEqualTo(1);
            } finally {
                jdbc.update("UPDATE bookable SET active = true WHERE id = ?", bookable);
            }
        }

        @Test
        @DisplayName("Performance: listing bookables costs a fixed number of queries, not one per item (no N+1)")
        void noNPlusOne() {
            long library = id("venue", "Central Library");
            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();

            http.get("/api/v1/venues/" + library + "/bookables?size=16", member);

            // venue check + page query (with types joined) + count query
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
            statistics.setStatisticsEnabled(false);
        }
    }

    @Nested
    @DisplayName("Countries")
    class Countries {

        @Test
        @DisplayName("FR-CAT-14: the country list comes from the IANA seed")
        void countries_FRCAT14() {
            Response response = http.get("/api/v1/countries", member);

            assertThat(response.status()).isEqualTo(200);
            assertThat(texts(response.body(), "code")).contains("GB", "IN", "US").isSorted();
        }

        @Test
        @DisplayName("FR-CAT-14: a one-zone country returns exactly that zone; the code is case-insensitive")
        void oneZoneCountry_FRCAT14() {
            Response response = http.get("/api/v1/countries/in/time-zones", member);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body().get("countryCode").asString()).isEqualTo("IN");
            assertThat(strings(response.body().get("timeZones"))).containsExactly("Asia/Kolkata");
        }

        @Test
        @DisplayName("FR-CAT-14: a country with several zones lists them all, sorted")
        void severalZones_FRCAT14() {
            List<String> zones = strings(http.get("/api/v1/countries/US/time-zones", member).body().get("timeZones"));

            assertThat(zones).contains("America/New_York", "America/Los_Angeles").isSorted();
        }

        private static List<String> strings(JsonNode array) {
            List<String> values = new ArrayList<>();
            array.forEach(item -> values.add(item.asString()));
            return values;
        }

        @Test
        @DisplayName("Unknown country → 404")
        void unknownCountry() {
            assertThat(http.get("/api/v1/countries/ZZ/time-zones", member).status()).isEqualTo(404);
        }
    }
}
