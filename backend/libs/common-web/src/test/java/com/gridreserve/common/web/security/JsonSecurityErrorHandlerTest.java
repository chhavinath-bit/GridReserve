package com.gridreserve.common.web.security;

import com.gridreserve.common.web.request.RequestIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSecurityErrorHandlerTest {

    private final JsonSecurityErrorHandler handler = new JsonSecurityErrorHandler(JsonMapper.builder().build(),
            Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC));

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.setAttribute(RequestIdFilter.MDC_KEY, "req-1");
        return request;
    }

    @Test
    @DisplayName("FR-ROLE-02: no or invalid token → 401 UNAUTHENTICATED in the standard format")
    void unauthenticated_FRROLE02() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request(), response, new InsufficientAuthenticationException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"UNAUTHENTICATED\"", "\"path\":\"/api/v1/me\"", "\"requestId\":\"req-1\"")
                .doesNotContain("no token");
    }

    @Test
    @DisplayName("FR-UI-07: an expired token → 401 TOKEN_EXPIRED, so the client knows to refresh")
    void expired_FRUI07() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JwtValidationException expired = new JwtValidationException("expired",
                List.of(new OAuth2Error(AccessTokenVerification.TOKEN_EXPIRED)));

        handler.commence(request(), response, new InvalidBearerTokenException("expired", expired));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"TOKEN_EXPIRED\"");
    }

    @Test
    @DisplayName("FR-ROLE-03: a valid token without the needed role → 403 FORBIDDEN")
    void forbidden_FRROLE03() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request(), response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }
}
