package com.gridreserve.booking.client.dto;

/** identity-service's token answer, read tolerantly: unknown fields are ignored. */
public record ServiceToken(String accessToken, long expiresIn) {

    @Override
    public String toString() {
        return "ServiceToken[expiresIn=" + expiresIn + "]";
    }
}
