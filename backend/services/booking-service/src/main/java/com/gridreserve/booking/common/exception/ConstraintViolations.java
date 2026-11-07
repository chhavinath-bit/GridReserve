package com.gridreserve.booking.common.exception;

import com.gridreserve.common.web.error.DomainException;
import com.gridreserve.common.web.error.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.spi.TemplatedViolatedConstraintNameExtractor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

/**
 * The one place where database constraint names become API errors (CLAUDE.md §7.3). The exclusion
 * constraints are the real guard against double-booking (BR-10, BR-11), so their names map to 409s.
 * A constraint that is not listed is a bug, so the original exception is rethrown and becomes a 500.
 */
public final class ConstraintViolations {

    private static final Map<String, Translation> BY_CONSTRAINT = Map.of(
            "ex_booking_bookable_overlap", new Translation(BookingErrorCode.SLOT_TAKEN,
                    "This time has just been taken. Please pick another slot."),
            "ex_booking_member_overlap", new Translation(BookingErrorCode.MEMBER_OVERLAP,
                    "You already have a booking at this time."));

    private static final String EXCLUSION_VIOLATION = "23P01";

    private ConstraintViolations() {
    }

    public static RuntimeException translate(DataIntegrityViolationException ex) {
        if (ex.getCause() instanceof ConstraintViolationException violation) {
            Translation translation = BY_CONSTRAINT.get(constraintName(violation));
            if (translation != null) {
                return new DomainException(translation.code(), translation.message());
            }
        }
        return ex;
    }

    /**
     * Hibernate reads the name for unique, check and foreign-key violations, but not for exclusion
     * violations (SQLState 23P01). For those, read it from PostgreSQL's message ({@code ... constraint
     * "name"}) with the same template Hibernate uses for the others.
     */
    private static String constraintName(ConstraintViolationException violation) {
        if (violation.getConstraintName() != null) {
            return violation.getConstraintName();
        }
        if (EXCLUSION_VIOLATION.equals(violation.getSQLState())) {
            return TemplatedViolatedConstraintNameExtractor.extractUsingTemplate("constraint \"", "\"",
                    violation.getSQLException().getMessage());
        }
        return null;
    }

    private record Translation(ErrorCode code, String message) {
    }
}
