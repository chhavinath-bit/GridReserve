package com.gridreserve.booking.booking.job;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** {@code gridreserve.idempotency.cleanup.*}: removing keys older than 24 hours (IDEM-07). Read at startup. */
@Validated
@ConfigurationProperties("gridreserve.idempotency.cleanup")
public record IdempotencyCleanupProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1h") @NotNull Duration interval) {
}
