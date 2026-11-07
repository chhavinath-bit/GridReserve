package com.gridreserve.common.web.error;

import com.gridreserve.common.web.request.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Turns exceptions into the standard error JSON. Messages are written for users;
 * stack traces, SQL and class names never leave the service (NFR-SEC-05).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ErrorResponse> handleDomain(DomainException ex, HttpServletRequest request) {
        return respond(ex.errorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        ErrorCode code = CommonErrorCode.VALIDATION_FAILED;
        ErrorResponse body = new ErrorResponse(now(), code.status().value(), code.code(),
                "Some fields are invalid.", request.getRequestURI(), requestId(request), fieldErrors);
        return ResponseEntity.status(code.status()).body(body);
    }

    // A query or path parameter that cannot be converted, e.g. ?date=tomorrow for a LocalDate.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                     HttpServletRequest request) {
        return respondWithField(ex.getName(), "Has an invalid format.", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                         HttpServletRequest request) {
        return respondWithField(ex.getParameterName(), "Is required.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(CommonErrorCode.MALFORMED_REQUEST, "The request body could not be read.", request);
    }

    // NoResourceFoundException is Spring's name for "no handler for this path".
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoHandler(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(CommonErrorCode.NOT_FOUND, "Nothing exists at this path.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(CommonErrorCode.METHOD_NOT_ALLOWED, "This method is not supported here.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(CommonErrorCode.INTERNAL_ERROR, "Something went wrong. Please try again.", request);
    }

    private ResponseEntity<ErrorResponse> respondWithField(String field, String message, HttpServletRequest request) {
        ErrorCode code = CommonErrorCode.VALIDATION_FAILED;
        ErrorResponse body = new ErrorResponse(now(), code.status().value(), code.code(),
                "Some fields are invalid.", request.getRequestURI(), requestId(request),
                List.of(new ErrorResponse.FieldError(field, message)));
        return ResponseEntity.status(code.status()).body(body);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(now(), code, message, request.getRequestURI(), requestId(request));
        return ResponseEntity.status(code.status()).body(body);
    }

    private Instant now() {
        return clock.instant();
    }

    private static String requestId(HttpServletRequest request) {
        return RequestIdFilter.currentRequestId(request);
    }
}
