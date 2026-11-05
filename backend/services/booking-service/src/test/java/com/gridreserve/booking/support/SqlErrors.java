package com.gridreserve.booking.support;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.postgresql.util.PSQLException;
import org.springframework.core.NestedExceptionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Asserts that a statement was rejected by one specific, named constraint. Checking the name (not just
 * "some error") matters: the service maps errors to API codes by constraint name.
 */
public final class SqlErrors {

    public static final String EXCLUSION_VIOLATION = "23P01";
    public static final String CHECK_VIOLATION = "23514";
    public static final String FOREIGN_KEY_VIOLATION = "23503";

    private SqlErrors() {
    }

    public static void assertRejectedBy(String sqlState, String constraint, ThrowingCallable statement) {
        assertThatThrownBy(statement).satisfies(thrown -> {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(thrown);
            assertThat(cause).isInstanceOf(PSQLException.class);
            PSQLException sqlError = (PSQLException) cause;
            assertThat(sqlError.getSQLState()).isEqualTo(sqlState);
            assertThat(sqlError.getServerErrorMessage()).isNotNull();
            assertThat(sqlError.getServerErrorMessage().getConstraint()).isEqualTo(constraint);
        });
    }
}
