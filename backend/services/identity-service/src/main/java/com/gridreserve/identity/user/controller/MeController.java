package com.gridreserve.identity.user.controller;

import com.gridreserve.identity.user.dto.UserResponse;
import com.gridreserve.identity.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    /** The signed-in user (FR-AUTH-08). Who that is comes only from the verified token's {@code sub}. */
    @GetMapping("/api/v1/me")
    public UserResponse me(@AuthenticationPrincipal Jwt token) {
        return userService.get(Long.parseLong(token.getSubject()));
    }
}
