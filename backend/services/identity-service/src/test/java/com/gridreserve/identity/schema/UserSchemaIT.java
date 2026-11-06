package com.gridreserve.identity.schema;

import com.gridreserve.identity.support.ServiceIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The identity_db migrations as PostgreSQL sees them (Hibernate already validated the mapping at startup). */
@ServiceIntegrationTest
class UserSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("FR-SEED-02: one ADMIN and two MEMBER demo accounts, with BCrypt hashes (cost 10), not plaintext")
    void demoAccountsSeeded_FRSEED02() {
        List<Map<String, Object>> seeded = jdbc.queryForList("""
                SELECT email, role, password_hash FROM app_user
                WHERE email LIKE '%@gridreserve.test' ORDER BY email""");

        assertThat(seeded).extracting(row -> row.get("email") + "/" + row.get("role")).containsExactly(
                "admin@gridreserve.test/ADMIN", "alice@gridreserve.test/MEMBER", "bob@gridreserve.test/MEMBER");
        assertThat(seeded).allSatisfy(row ->
                assertThat((String) row.get("password_hash")).matches("\\$2a\\$10\\$[./A-Za-z0-9]{53}"));
    }

    @Test
    @DisplayName("FR-AUTH-02: the database itself refuses an email that is not lower-cased")
    void upperCaseEmailRejected_FRAUTH02() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO app_user (email, password_hash, display_name, role, created_at)
                VALUES ('Someone@Example.test', 'x', 'X', 'MEMBER', now())"""))
                .satisfies(thrown -> {
                    PSQLException cause = (PSQLException) NestedExceptionUtils.getMostSpecificCause(thrown);
                    assertThat(cause.getServerErrorMessage().getConstraint()).isEqualTo("ck_app_user_email_lower");
                });
    }
}
