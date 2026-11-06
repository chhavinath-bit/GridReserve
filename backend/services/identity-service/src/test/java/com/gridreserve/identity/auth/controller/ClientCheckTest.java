package com.gridreserve.identity.auth.controller;

import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.DomainException;
import com.gridreserve.identity.security.FrontendProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientCheckTest {

    private final ClientCheck check = new ClientCheck(new FrontendProperties("http://localhost:5173"));

    private static MockHttpServletRequest request(String origin, String client) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        if (client != null) {
            request.addHeader("X-GridReserve-Client", client);
        }
        return request;
    }

    @Test
    @DisplayName("FR-AUTH-12: our origin and the client header → allowed")
    void ownOriginAndHeaderAllowed_FRAUTH12() {
        assertThatCode(() -> check.verify(request("http://localhost:5173", "web"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FS-40: a foreign or missing Origin, or a missing or wrong client header → 403 FORBIDDEN")
    void anythingElseForbidden_FS40() {
        for (MockHttpServletRequest bad : new MockHttpServletRequest[] {
                request("https://evil.example", "web"),
                request(null, "web"),
                request("http://localhost:5173", null),
                request("http://localhost:5173", "other"),
                // Close is not enough: a different port is a different origin.
                request("http://localhost:5174", "web")}) {
            assertThatThrownBy(() -> check.verify(bad))
                    .isInstanceOfSatisfying(DomainException.class,
                            ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
        }
    }
}
