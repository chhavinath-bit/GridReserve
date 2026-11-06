package com.gridreserve.identity.auth.controller;

import com.gridreserve.identity.auth.dto.LoginRequest;
import com.gridreserve.identity.auth.dto.TokenResponse;
import com.gridreserve.identity.auth.service.AuthService;
import com.gridreserve.identity.user.dto.RegisterRequest;
import com.gridreserve.identity.user.dto.UserResponse;
import com.gridreserve.identity.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints: anyone may register or log in. Refresh and logout follow in M1.3b. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
