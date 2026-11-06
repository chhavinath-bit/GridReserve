package com.gridreserve.identity.auth.service;

import com.gridreserve.identity.auth.dto.TokenResponse;

/** What a login hands out: the access token for the response body and the refresh token for the cookie. */
public record Session(TokenResponse accessToken, String refreshToken) {

    // Never print the refresh token, even if the record is logged by accident.
    @Override
    public String toString() {
        return "Session[" + accessToken + "]";
    }
}
