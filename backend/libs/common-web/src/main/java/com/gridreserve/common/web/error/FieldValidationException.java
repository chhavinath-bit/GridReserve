package com.gridreserve.common.web.error;

import java.util.List;

/**
 * Invalid fields that only the service can detect, because the check needs stored data (a time zone
 * of the chosen country, a duration measured against the bookable type's slot length). Answers {@code
 * 400 VALIDATION_FAILED} with {@code fieldErrors}, exactly like a Bean Validation failure, so clients
 * handle both the same way.
 */
public class FieldValidationException extends DomainException {

    private final List<ErrorResponse.FieldError> fieldErrors;

    public FieldValidationException(List<ErrorResponse.FieldError> fieldErrors) {
        super(CommonErrorCode.VALIDATION_FAILED, "Some fields are invalid.");
        if (fieldErrors.isEmpty()) {
            throw new IllegalArgumentException("A validation failure needs at least one field error");
        }
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<ErrorResponse.FieldError> fieldErrors() {
        return fieldErrors;
    }
}
