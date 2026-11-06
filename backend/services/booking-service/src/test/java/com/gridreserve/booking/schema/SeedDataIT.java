package com.gridreserve.booking.schema;

import com.gridreserve.booking.catalog.entity.BookableType;
import com.gridreserve.booking.catalog.entity.BookingModel;
import com.gridreserve.booking.catalog.entity.Category;
import com.gridreserve.booking.catalog.entity.CountryTimeZone;
import com.gridreserve.booking.catalog.entity.Venue;
import com.gridreserve.booking.catalog.repository.BookableTypeRepository;
import com.gridreserve.booking.catalog.repository.CategoryRepository;
import com.gridreserve.booking.catalog.repository.CountryTimeZoneRepository;
import com.gridreserve.booking.catalog.repository.VenueRepository;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The seed data matches the decided catalog (CLAUDE.md §15.1, REQUIREMENTS.md table 2.3).
 * Reads go through the JPA repositories, so the entity mappings are exercised, not only validated.
 */
@ServiceIntegrationTest
@Transactional // keeps the session open for lazy associations; read-only test, rolled back
class SeedDataIT {

    @Autowired
    CategoryRepository categories;

    @Autowired
    BookableTypeRepository bookableTypes;

    @Autowired
    VenueRepository venues;

    @Autowired
    CountryTimeZoneRepository countryTimeZones;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("FR-CAT-01: exactly five categories, each with cutoff 120 min, limit 3, horizon 7 days")
    void fiveCategories_FRCAT01() {
        assertThat(categories.findAll())
                .extracting(Category::getCode, Category::getCancellationCutoffMinutes,
                        Category::getMaxActiveBookingsPerMember, Category::getBookingHorizonDays)
                .containsExactlyInAnyOrder(
                        tuple("STUDY", 120, 3, 7), tuple("SPORTS", 120, 3, 7), tuple("PARKING", 120, 3, 7),
                        tuple("CLINIC", 120, 3, 7), tuple("GYM", 120, 3, 7));
    }

    @Test
    @DisplayName("FR-CAT-01/02: bookable types with labels, models and default rules from table 2.3")
    void bookableTypes_FRCAT01() {
        assertThat(bookableTypes.findAll())
                .extracting(t -> t.getCategory().getCode(), BookableType::getCode, BookableType::getLabel,
                        BookableType::getBookingModel, BookableType::getSlotMinutes,
                        BookableType::getMinDurationMinutes, BookableType::getMaxDurationMinutes,
                        BookableType::getBufferMinutes)
                .containsExactlyInAnyOrder(
                        tuple("STUDY", "ROOM", "Room", BookingModel.EXCLUSIVE, 30, 30, 180, 0),
                        tuple("STUDY", "SEAT", "Seat", BookingModel.EXCLUSIVE, 60, 60, 240, 0),
                        tuple("STUDY", "HALL", "Hall", BookingModel.CAPACITY, null, null, null, 0),
                        tuple("SPORTS", "COURT", "Court", BookingModel.EXCLUSIVE, 60, 60, 120, 0),
                        tuple("PARKING", "BAY", "Bay", BookingModel.EXCLUSIVE, 60, 60, 720, 0),
                        tuple("CLINIC", "DOCTOR", "Doctor", BookingModel.EXCLUSIVE, 30, 30, 30, 0),
                        tuple("GYM", "TRAINER", "Trainer", BookingModel.EXCLUSIVE, 15, 45, 45, 15),
                        tuple("GYM", "CLASS", "Class", BookingModel.CAPACITY, null, null, null, 0));
    }

    @Test
    @DisplayName("FR-CAT-03: one venue per category, with country and zone; IronWorks is in London")
    void venues_FRCAT03() {
        Map<String, Venue> byName = venues.findAll().stream()
                .collect(Collectors.toMap(Venue::getName, Function.identity()));

        assertThat(byName).containsOnlyKeys(
                "Central Library", "Sunrise Turf", "Palm Apartments", "CityCare Clinic", "IronWorks");
        assertThat(byName.get("Central Library").getTimeZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(byName.get("IronWorks").getCountryCode()).isEqualTo("GB");
        assertThat(byName.get("IronWorks").getTimeZone()).isEqualTo(ZoneId.of("Europe/London"));
    }

    @Test
    @DisplayName("M1.2: Central Library has 4 rooms and at least 10 individually bookable seats")
    void librarySeats() {
        Map<String, Integer> countByType = jdbc.query("""
                SELECT t.code, count(*) AS n FROM bookable b
                JOIN bookable_type t ON t.id = b.bookable_type_id
                JOIN venue v ON v.id = b.venue_id
                WHERE v.name = 'Central Library' GROUP BY t.code
                """, rs -> {
            Map<String, Integer> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getString("code"), rs.getInt("n"));
            }
            return result;
        });

        assertThat(countByType.get("ROOM")).isEqualTo(4);
        assertThat(countByType.get("SEAT")).isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("A-06: every venue has opening hours; CityCare Clinic is closed on Sunday")
    void openingHours_A06() {
        List<String> venuesWithoutHours = jdbc.queryForList("""
                SELECT v.name FROM venue v
                WHERE NOT EXISTS (SELECT 1 FROM venue_opening_hours h WHERE h.venue_id = v.id)
                """, String.class);
        Integer clinicSundayRows = jdbc.queryForObject("""
                SELECT count(*) FROM venue_opening_hours h JOIN venue v ON v.id = h.venue_id
                WHERE v.name = 'CityCare Clinic' AND h.iso_weekday = 7
                """, Integer.class);

        assertThat(venuesWithoutHours).isEmpty();
        assertThat(clinicSundayRows).isZero();
    }

    @Test
    @DisplayName("FR-CAT-14: the IANA list covers all countries, and every seeded zone is a valid java.time.ZoneId")
    void everyZoneKnownToJava_FRCAT14() {
        List<CountryTimeZone> pairs = countryTimeZones.findAll();

        assertThat(pairs).hasSizeGreaterThan(400);
        assertThat(pairs.stream().map(CountryTimeZone::getCountryCode).distinct().count()).isGreaterThan(240);
        // Fails if the IANA release is newer than the JDK's tzdata and adds a zone Java does not know.
        for (CountryTimeZone pair : pairs) {
            assertThatCode(pair::getTimeZone).as(pair.getCountryCode()).doesNotThrowAnyException();
        }
        assertThat(pairs).anySatisfy(p -> {
            assertThat(p.getCountryCode()).isEqualTo("IN");
            assertThat(p.getTimeZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        });
    }
}
