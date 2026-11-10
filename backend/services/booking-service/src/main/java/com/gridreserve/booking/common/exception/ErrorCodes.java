package com.gridreserve.booking.common.exception;

import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.ErrorCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Every error code booking-service can answer, by name: for replaying a stored refusal (IDEM-03). */
public final class ErrorCodes {

    private static final Map<String, ErrorCode> BY_NAME = new HashMap<>();

    static {
        for (CommonErrorCode code : CommonErrorCode.values()) {
            BY_NAME.put(code.code(), code);
        }
        for (BookingErrorCode code : BookingErrorCode.values()) {
            BY_NAME.put(code.code(), code);
        }
    }

    private ErrorCodes() {
    }

    public static Optional<ErrorCode> byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }
}
