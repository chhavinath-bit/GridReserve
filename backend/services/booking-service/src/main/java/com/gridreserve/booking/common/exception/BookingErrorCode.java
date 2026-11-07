package com.gridreserve.booking.common.exception;

import com.gridreserve.common.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Error codes only booking-service produces (REQUIREMENTS.md §15.3). Grows with each milestone. */
public enum BookingErrorCode implements ErrorCode {

    // 409: the request was fine, but the data changed under it (someone else got there first, or the state moved on).
    SLOT_TAKEN(HttpStatus.CONFLICT),
    MEMBER_OVERLAP(HttpStatus.CONFLICT),
    HOLD_EXPIRED(HttpStatus.CONFLICT),
    INVALID_STATE(HttpStatus.CONFLICT),

    // 422: the request breaks a booking rule; sending it again unchanged will fail again.
    BOOKABLE_INACTIVE(HttpStatus.UNPROCESSABLE_CONTENT),
    START_IN_PAST(HttpStatus.UNPROCESSABLE_CONTENT),
    BEYOND_HORIZON(HttpStatus.UNPROCESSABLE_CONTENT),
    OUTSIDE_OPERATING_HOURS(HttpStatus.UNPROCESSABLE_CONTENT),
    OFF_GRID(HttpStatus.UNPROCESSABLE_CONTENT),
    DURATION_INVALID(HttpStatus.UNPROCESSABLE_CONTENT),
    MEMBER_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_CONTENT),
    CUTOFF_PASSED(HttpStatus.UNPROCESSABLE_CONTENT),
    // Time-slot booking and availability only work for EXCLUSIVE bookables (BR-09); CAPACITY from Phase 2.
    WRONG_BOOKING_MODEL(HttpStatus.UNPROCESSABLE_CONTENT);

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
