package com.gridreserve.booking.schema;

import com.gridreserve.booking.support.ServiceIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7 (D-48) on a database that already has venues with typed cities, as a local database from M1.13 may.
 * The shared test database starts empty, so this test migrates a scratch database to V6, adds such venues,
 * then applies V7. No venue may be lost or end up without a city.
 */
@ServiceIntegrationTest
class CityMigrationIT {

    private static final String DATABASE = "city_migration_check";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PostgreSQLContainer postgres;

    @AfterEach
    void dropScratchDatabase() {
        jdbc.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
    }

    @Test
    @DisplayName("FR-CAT-16/D-48: V7 links typed cities to the list in any case and spacing, and adds unlisted ones once")
    void typedCitiesAreLinked_FRCAT16() {
        jdbc.execute("CREATE DATABASE " + DATABASE);
        DriverManagerDataSource scratch = new DriverManagerDataSource(
                postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + DATABASE),
                postgres.getUsername(), postgres.getPassword());
        JdbcTemplate db = new JdbcTemplate(scratch);

        migrate(scratch, "6");
        venue(db, "Typed Mumbai", " mumbai ", "IN", "Asia/Kolkata");
        venue(db, "Typed Unknown", "Unknown", "IN", "Asia/Kolkata");
        venue(db, "Springfield One", "Springfield", "US", "America/Chicago");
        venue(db, "Springfield Two", "springfield", "US", "America/Chicago");
        migrate(scratch, "7");

        assertThat(cityOf(db, "Typed Mumbai")).isEqualTo("Mumbai");                 // the listed city
        assertThat(cityOf(db, "Typed Unknown")).isEqualTo("Unknown");               // kept, added to the list
        assertThat(cityOf(db, "Springfield One")).isEqualTo("Springfield");         // the oldest spelling wins
        assertThat(cityOf(db, "Springfield Two")).isEqualTo("Springfield");
        assertThat(db.queryForObject("SELECT count(*) FROM city WHERE lower(name) = 'springfield'", Integer.class))
                .isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM venue WHERE city_id IS NULL", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM venue", Integer.class)).isEqualTo(5 + 4);  // seeds + ours
    }

    private static void migrate(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).target(target).load().migrate();
    }

    private static void venue(JdbcTemplate db, String name, String city, String countryCode, String zone) {
        db.update("""
                INSERT INTO venue (category_id, name, city, country_code, time_zone, created_at, updated_at)
                SELECT id, ?, ?, ?, ?, now(), now() FROM category WHERE code = 'STUDY'
                """, name, city, countryCode, zone);
    }

    private static String cityOf(JdbcTemplate db, String venue) {
        return db.queryForObject("SELECT c.name FROM venue v JOIN city c ON c.id = v.city_id WHERE v.name = ?",
                String.class, venue);
    }
}
