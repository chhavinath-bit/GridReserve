package com.gridreserve.identity.ping.controller;

import com.gridreserve.identity.ping.dto.PingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Temporary platform check for M1.1 (removed once real endpoints exist): proves routing through
 * the gateway and discovery through Eureka.
 */
@RestController
public class PingController {

    private final String instance;

    public PingController(@Value("${HOSTNAME:local}") String instance) {
        this.instance = instance;
    }

    @GetMapping("/api/v1/ping")
    public PingResponse ping() {
        return new PingResponse("identity-service", instance);
    }

    /** Target of booking-service's Feign spike. /internal/** is never routed by the gateway. */
    @GetMapping("/internal/ping")
    public PingResponse internalPing() {
        return new PingResponse("identity-service", instance);
    }
}
