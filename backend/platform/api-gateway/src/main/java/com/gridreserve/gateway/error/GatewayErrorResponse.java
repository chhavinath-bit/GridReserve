package com.gridreserve.gateway.error;

import java.time.Instant;

/**
 * Same JSON shape as common-web's ErrorResponse (REQUIREMENTS.md API-05). Kept separate on purpose:
 * the gateway is reactive and uses only common-web's token verification, not its servlet error handling.
 */
public record GatewayErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId) {
}
