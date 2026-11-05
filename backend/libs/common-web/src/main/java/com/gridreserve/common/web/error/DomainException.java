package com.gridreserve.common.web.error;

/**
 * Thrown by services for expected business failures. The global handler turns it into the
 * standard error JSON with the code's HTTP status, so controllers never build error bodies.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    public DomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
