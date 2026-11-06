package com.gridreserve.identity.common.exception;

import com.gridreserve.common.web.error.DomainException;
import com.gridreserve.common.web.error.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

/**
 * The one place where database constraint names become API errors (CLAUDE.md §7.3). A constraint
 * that is not listed is a bug, so the original exception is rethrown and becomes a 500.
 */
public final class ConstraintViolations {

    private static final Map<String, Translation> BY_CONSTRAINT = Map.of(
            "uq_app_user_email", new Translation(IdentityErrorCode.EMAIL_TAKEN,
                    "An account with this email already exists."));

    private ConstraintViolations() {
    }

    public static RuntimeException translate(DataIntegrityViolationException ex) {
        if (ex.getCause() instanceof ConstraintViolationException violation
                && violation.getConstraintName() != null) {
            Translation translation = BY_CONSTRAINT.get(violation.getConstraintName());
            if (translation != null) {
                return new DomainException(translation.code(), translation.message());
            }
        }
        return ex;
    }

    private record Translation(ErrorCode code, String message) {
    }
}
