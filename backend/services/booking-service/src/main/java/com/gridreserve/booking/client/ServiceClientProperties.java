package com.gridreserve.booking.client;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * This service's own credentials for getting service tokens from identity-service (D-19). The secret
 * comes from the environment (BOOKING_SERVICE_CLIENT_SECRET), never from the config repo.
 */
@Validated
@ConfigurationProperties("gridreserve.service-client")
public record ServiceClientProperties(@NotBlank String id, @NotBlank String secret) {

    @Override
    public String toString() {
        return "ServiceClientProperties[id=" + id + ", secret=<hidden>]";
    }
}
