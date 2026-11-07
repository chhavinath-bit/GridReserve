package com.gridreserve.booking.common.exception;

import com.gridreserve.common.web.error.DomainException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintViolationsTest {

    // What PostgreSQL sends for an exclusion violation; Hibernate leaves the constraint name empty.
    private static DataIntegrityViolationException exclusion(String constraint) {
        SQLException sql = new SQLException(
                "ERROR: conflicting key value violates exclusion constraint \"" + constraint + "\"", "23P01");
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", sql, null));
    }

    @Test
    @DisplayName("BR-10 / BR-11: the two exclusion constraints map to 409 SLOT_TAKEN and MEMBER_OVERLAP")
    void exclusionConstraintsMapped() {
        assertThat(ConstraintViolations.translate(exclusion("ex_booking_bookable_overlap")))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(BookingErrorCode.SLOT_TAKEN));
        assertThat(ConstraintViolations.translate(exclusion("ex_booking_member_overlap")))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(BookingErrorCode.MEMBER_OVERLAP));
    }

    @Test
    @DisplayName("An unknown constraint is a bug: the original exception is kept (→ 500), never a guessed 409")
    void unknownConstraintKept() {
        DataIntegrityViolationException unknown = exclusion("ex_something_new");
        assertThat(ConstraintViolations.translate(unknown)).isSameAs(unknown);
    }
}
