package com.gridreserve.identity.auth.controller;

import com.gridreserve.common.web.error.DomainException;
import com.gridreserve.identity.auth.dto.LoginRequest;
import com.gridreserve.identity.auth.dto.TokenResponse;
import com.gridreserve.identity.auth.service.AuthService;
import com.gridreserve.identity.auth.service.Session;
import com.gridreserve.identity.common.exception.IdentityErrorCode;
import com.gridreserve.identity.user.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoints. Register and login need no proof of identity; refresh and logout are proved by
 * the refresh cookie and protected against CSRF by {@link ClientCheck}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;
    private final ClientCheck clientCheck;

    AuthController(AuthService authService, RefreshCookie refreshCookie, ClientCheck clientCheck) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
        this.clientCheck = clientCheck;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withSession(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withSession(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
        clientCheck.verify(request);
        String token = refreshCookie.read(request).orElseThrow(() ->
                new DomainException(IdentityErrorCode.REFRESH_TOKEN_INVALID, "Your session has ended. Log in again."));
        TokenResponse accessToken = authService.refresh(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.set(token))
                .body(accessToken);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        clientCheck.verify(request);
        refreshCookie.read(request).ifPresent(authService::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
                .build();
    }

    private ResponseEntity<TokenResponse> withSession(HttpStatus status, Session session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.set(session.refreshToken()))
                .body(session.accessToken());
    }
}
