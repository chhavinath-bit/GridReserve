package com.gridreserve.booking.common.exception;

import com.gridreserve.common.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Error codes only booking-service produces (REQUIREMENTS.md §15.3). Grows with each milestone. */
public enum BookingErrorCode implements ErrorCode {

    BEYOND_HORIZON(HttpStatus.UNPROCESSABLE_CONTENT),
    // CAPACITY bookables (halls, classes) are listed in Phase 1 but bookable only from Phase 2.
    BOOKING_MODEL_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT);

    private final HttpStatus status;

    BookingErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
