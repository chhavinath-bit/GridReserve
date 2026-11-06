package com.gridreserve.identity.schema;

import com.gridreserve.identity.support.ServiceIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V3 as PostgreSQL sees it: the named constraints and indexes the code relies on. */
@ServiceIntegrationTest
class RefreshTokenSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    private String violatedConstraint(Runnable statement) {
        Throwable[] thrown = new Throwable[1];
        assertThatThrownBy(statement::run).satisfies(ex -> thrown[0] = ex);
        PSQLException cause = (PSQLException) NestedExceptionUtils.getMostSpecificCause(thrown[0]);
        return cause.getServerErrorMessage().getConstraint();
    }

    @Test
    @DisplayName("FR-AUTH-09: a token hash can exist only once, and only for an existing user")
    void hashUniqueAndUserMustExist_FRAUTH09() {
        String hash = "a".repeat(64);
        jdbc.update("""
                INSERT INTO refresh_token (user_id, token_hash, created_at, expires_at)
                VALUES (2, ?, now(), now() + interval '7 days')""", hash);

        assertThat(violatedConstraint(() -> jdbc.update("""
                INSERT INTO refresh_token (user_id, token_hash, created_at, expires_at)
                VALUES (3, ?, now(), now() + interval '7 days')""", hash))).isEqualTo("uq_refresh_token_hash");
        assertThat(violatedConstraint(() -> jdbc.update("""
                INSERT INTO refresh_token (user_id, token_hash, created_at, expires_at)
                VALUES (999999, ?, now(), now() + interval '7 days')""", "b".repeat(64))))
                .isEqualTo("fk_refresh_token_user");

        jdbc.update("DELETE FROM refresh_token WHERE token_hash = ?", hash);
    }

    @Test
    @DisplayName("D-37: the cleanup at login and lookups by user have their indexes")
    void indexesExist_D37() {
        assertThat(jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'refresh_token' ORDER BY indexname", String.class))
                .containsExactly("ix_refresh_token_expires", "ix_refresh_token_user", "pk_refresh_token",
                        "uq_refresh_token_hash");
    }
}
