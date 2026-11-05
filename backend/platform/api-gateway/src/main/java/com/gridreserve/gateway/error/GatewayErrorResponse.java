package com.gridreserve.gateway.error;

import java.time.Instant;

/**
 * Same JSON shape as common-web's ErrorResponse (REQUIREMENTS.md API-05). Duplicated on purpose:
 * the gateway is reactive and must not depend on the servlet-based common-web library.
 */
public record GatewayErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId) {
}
