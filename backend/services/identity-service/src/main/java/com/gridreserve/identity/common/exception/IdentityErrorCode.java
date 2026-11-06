package com.gridreserve.identity.common.exception;

import com.gridreserve.common.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Error codes only identity-service produces (REQUIREMENTS.md §12 error catalog). */
public enum IdentityErrorCode implements ErrorCode {

    EMAIL_TAKEN(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED);

    private final HttpStatus status;

    IdentityErrorCode(HttpStatus status) {
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
