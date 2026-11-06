package com.gridreserve.common.web.security;

import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.ErrorCode;
import com.gridreserve.common.web.error.ErrorResponse;
import com.gridreserve.common.web.request.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;

/**
 * Writes security failures in the standard error format (API-05). Security errors happen in the
 * filter chain, before any controller, so the {@code @RestControllerAdvice} never sees them.
 * <ul>
 *   <li>no token, or a bad one → {@code 401 UNAUTHENTICATED} (FR-ROLE-02);</li>
 *   <li>a valid but expired token → {@code 401 TOKEN_EXPIRED}, the client's signal to refresh (FR-UI-07);</li>
 *   <li>a valid token with the wrong role → {@code 403 FORBIDDEN} (FR-ROLE-03).</li>
 * </ul>
 * The reason a token was rejected is never sent to the client.
 */
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;
    private final Clock clock;

    public JsonSecurityErrorHandler(JsonMapper jsonMapper, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        if (AccessTokenVerification.isExpired(ex)) {
            write(request, response, CommonErrorCode.TOKEN_EXPIRED, "Your session has expired.");
        } else {
            write(request, response, CommonErrorCode.UNAUTHENTICATED, "Sign in to continue.");
        }
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(request, response, CommonErrorCode.FORBIDDEN, "You do not have permission to do this.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       ErrorCode code, String message) throws IOException {
        ErrorResponse body = ErrorResponse.of(clock.instant(), code, message, request.getRequestURI(),
                RequestIdFilter.currentRequestId(request));
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
