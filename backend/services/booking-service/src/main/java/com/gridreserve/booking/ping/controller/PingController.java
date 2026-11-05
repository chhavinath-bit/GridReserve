package com.gridreserve.booking.ping.controller;

import com.gridreserve.booking.ping.dto.PingResponse;
import com.gridreserve.booking.client.IdentityClient;
import com.gridreserve.booking.client.dto.IdentityPing;
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
    private final IdentityClient identityClient;

    public PingController(@Value("${HOSTNAME:local}") String instance, IdentityClient identityClient) {
        this.instance = instance;
        this.identityClient = identityClient;
    }

    @GetMapping("/api/v1/ping")
    public PingResponse ping() {
        return new PingResponse("booking-service", instance);
    }

    /** Spike S1/S2: booking calls identity through Eureka + Feign; returns the fallback if identity is down. */
    @GetMapping("/api/v1/ping/identity")
    public IdentityPing pingIdentity() {
        return identityClient.ping();
    }
}
