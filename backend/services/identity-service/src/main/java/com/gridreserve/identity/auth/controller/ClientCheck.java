package com.gridreserve.identity.auth.controller;

import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.DomainException;
import com.gridreserve.identity.security.FrontendProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * CSRF defence for the two endpoints a cookie authenticates (FR-AUTH-12). The browser sends the
 * refresh cookie automatically, so another site could trigger a request; it cannot fake the
 * {@code Origin} header, and a cross-site request with a custom header would need CORS approval.
 * Both are checked, so either one alone still protects if the other is ever weakened.
 */
@Component
class ClientCheck {

    static final String CLIENT_HEADER = "X-GridReserve-Client";
    static final String WEB_CLIENT = "web";

    private final FrontendProperties frontend;

    ClientCheck(FrontendProperties frontend) {
        this.frontend = frontend;
    }

    void verify(HttpServletRequest request) {
        boolean ownOrigin = frontend.frontendOrigin().equals(request.getHeader(HttpHeaders.ORIGIN));
        boolean ownClient = WEB_CLIENT.equals(request.getHeader(CLIENT_HEADER));
        if (!ownOrigin || !ownClient) {
            throw new DomainException(CommonErrorCode.FORBIDDEN, "This request is not allowed from here.");
        }
    }
}
