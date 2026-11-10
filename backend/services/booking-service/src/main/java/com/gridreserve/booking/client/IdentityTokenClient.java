package com.gridreserve.booking.client;

import com.gridreserve.booking.client.dto.ServiceToken;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Asks identity-service for a service token with this service's client credentials (HTTP Basic). A
 * separate client from {@link IdentityClient}, because that one sends the token this one fetches.
 */
@FeignClient(name = "identity-service", contextId = "identity-service-tokens", configuration = IdentityErrors.class)
public interface IdentityTokenClient {

    @PostMapping("/internal/service-tokens")
    ServiceToken serviceToken(@RequestHeader("Authorization") String basicCredentials);
}
