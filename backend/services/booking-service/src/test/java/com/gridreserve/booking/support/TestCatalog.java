package com.gridreserve.booking.support;

import com.gridreserve.booking.support.Http.Response;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Venues and bookables that a test creates through the admin API, and removes again afterwards: tests in
 * the same Spring context share one database, and other tests count the seeded catalog. Every name starts
 * with {@link #PREFIX}, so cleanup finds exactly these rows. Venues are in India (Asia/Kolkata), open every
 * day 00:00-24:00 unless a test says otherwise.
 */
public final class TestCatalog {

    public static final String PREFIX = "Test ";

    private final Http http;
    private final JdbcTemplate jdbc;

    public TestCatalog(Http http, JdbcTemplate jdbc) {
        this.http = http;
        this.jdbc = jdbc;
    }

    public long categoryId(String code) {
        return jdbc.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
    }

    public long typeId(String code) {
        return jdbc.queryForObject("SELECT id FROM bookable_type WHERE code = ?", Long.class, code);
    }

    public static List<Map<String, Object>> everyDay(String opensAt, String closesAt) {
        List<Map<String, Object>> days = new ArrayList<>();
        for (String day : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")) {
            days.add(Map.of("weekday", day, "opensAt", opensAt, "closesAt", closesAt));
        }
        return days;
    }

    public static Map<String, Object> venueBody(long categoryId, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", PREFIX + name);
        body.put("addressLine", "1 Test Road");
        body.put("city", "Testville");
        body.put("countryCode", "IN");
        body.put("timeZone", "Asia/Kolkata");
        body.put("openingHours", everyDay("00:00", "24:00"));
        return body;
    }

    public static Map<String, Object> bookableBody(long typeId, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bookableTypeId", typeId);
        body.put("name", PREFIX + name);
        return body;
    }

    /** A new venue in that category; returns its ID. */
    public long venue(String categoryCode, String name) {
        Response created = http.post("/api/v1/venues", TestJwtKeys.adminToken(), venueBody(categoryId(categoryCode), name));
        assertThat(created.status()).as("create venue: %s", created.body()).isEqualTo(201);
        return created.body().get("id").asLong();
    }

    /** A new bookable of that type at the venue, without overrides; returns its ID. */
    public long bookable(long venueId, String typeCode, String name) {
        Response created = http.post("/api/v1/venues/" + venueId + "/bookables", TestJwtKeys.adminToken(),
                bookableBody(typeId(typeCode), name));
        assertThat(created.status()).as("create bookable: %s", created.body()).isEqualTo(201);
        return created.body().get("id").asLong();
    }

    /** Removes every test venue, its bookables, their hours and their bookings. */
    public void removeAll() {
        String bookables = "SELECT id FROM bookable WHERE name LIKE '" + PREFIX + "%'";
        String venues = "SELECT id FROM venue WHERE name LIKE '" + PREFIX + "%'";
        TestBookings.delete(jdbc, "bookable_id IN (" + bookables + ")");
        jdbc.update("DELETE FROM bookable_opening_hours WHERE bookable_id IN (" + bookables + ")");
        jdbc.update("DELETE FROM bookable WHERE venue_id IN (" + venues + ") OR name LIKE '" + PREFIX + "%'");
        jdbc.update("DELETE FROM venue_opening_hours WHERE venue_id IN (" + venues + ")");
        jdbc.update("DELETE FROM venue WHERE name LIKE '" + PREFIX + "%'");
    }
}
