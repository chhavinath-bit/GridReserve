package com.gridreserve.common.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The one error format used by every service (REQUIREMENTS.md API-05).
 * {@code fieldErrors} is omitted unless the request failed validation.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(Instant timestamp, ErrorCode errorCode, String message,
                                   String path, String requestId) {
        return new ErrorResponse(timestamp, errorCode.status().value(), errorCode.code(), message,
                path, requestId, List.of());
    }
}
