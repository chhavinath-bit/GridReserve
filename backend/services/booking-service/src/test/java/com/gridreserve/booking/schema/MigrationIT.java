package com.gridreserve.booking.schema;

import com.gridreserve.booking.support.ServiceIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Flyway migrations apply to an empty PostgreSQL 16 and produce the expected schema. Hibernate's
 * ddl-auto=validate has already compared every entity with these tables when the context started.
 */
@ServiceIntegrationTest
class MigrationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("M1.2/M1.13/M1.13b: V1-V7 are applied in order and all succeeded")
    void allMigrationsApplied() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7");
    }

    @Test
    @DisplayName("BR-10: the btree_gist extension needed by the exclusion constraints is installed")
    void btreeGistInstalled() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-10/BR-11: both exclusion constraints exist under the names the error mapping will use")
    void exclusionConstraintsExist() {
        List<String> names = jdbc.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid = 'booking'::regclass AND contype = 'x'", String.class);

        assertThat(names).containsExactlyInAnyOrder("ex_booking_bookable_overlap", "ex_booking_member_overlap");
    }

    @Test
    @DisplayName("M1.2: every constraint in the schema is explicitly named (no generated *_check / *_fkey names)")
    void noGeneratedConstraintNames() {
        List<String> generated = jdbc.queryForList("""
                SELECT conname FROM pg_constraint c
                JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = 'public'
                  AND (conname LIKE '%\\_check' OR conname LIKE '%\\_fkey' OR conname LIKE '%\\_pkey' OR conname LIKE '%\\_key')
                """, String.class);

        assertThat(generated).isEmpty();
    }
}
