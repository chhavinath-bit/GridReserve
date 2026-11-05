package com.gridreserve.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * A machine-readable error code plus the HTTP status it maps to.
 * Each service defines its own enum of codes (e.g. SLOT_TAKEN); {@link CommonErrorCode} holds the shared ones.
 */
public interface ErrorCode {

    /** Stable code sent to clients, e.g. {@code "VALIDATION_FAILED"}. */
    String code();

    HttpStatus status();
}
