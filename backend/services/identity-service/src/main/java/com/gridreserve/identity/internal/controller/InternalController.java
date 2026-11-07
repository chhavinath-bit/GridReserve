package com.gridreserve.identity.internal.controller;

import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.DomainException;
import com.gridreserve.identity.auth.dto.TokenResponse;
import com.gridreserve.identity.internal.dto.InternalUserResponse;
import com.gridreserve.identity.internal.service.ServiceTokenIssuer;
import com.gridreserve.identity.user.repository.AppUserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Endpoints for other services only (EVT-05): the gateway never routes {@code /internal/**}. Getting a
 * token is authenticated with the client's ID and secret (HTTP Basic); everything else needs a service
 * token (role SERVICE, SecurityConfig).
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private static final int MAX_IDS = 100;

    private final ServiceTokenIssuer tokens;
    private final AppUserRepository users;

    public InternalController(ServiceTokenIssuer tokens, AppUserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @PostMapping("/service-tokens")
    public TokenResponse serviceToken(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            throw new DomainException(CommonErrorCode.UNAUTHENTICATED, "Unknown service client.");
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notBase64) {
            throw new DomainException(CommonErrorCode.UNAUTHENTICATED, "Unknown service client.");
        }
        int colon = decoded.indexOf(':');
        if (colon < 1) {
            throw new DomainException(CommonErrorCode.UNAUTHENTICATED, "Unknown service client.");
        }
        return tokens.issue(decoded.substring(0, colon), decoded.substring(colon + 1));
    }

    /** Names and emails for a batch of user IDs (FR-ADM-01); unknown IDs are simply left out. */
    @GetMapping("/users")
    public List<InternalUserResponse> users(@RequestParam List<Long> ids) {
        if (ids.isEmpty() || ids.size() > MAX_IDS) {
            throw new DomainException(CommonErrorCode.VALIDATION_FAILED, "Ask for 1 to " + MAX_IDS + " users at a time.");
        }
        return users.findAllById(ids).stream()
                .map(user -> new InternalUserResponse(user.getId(), user.getEmail(), user.getDisplayName()))
                .toList();
    }
}
