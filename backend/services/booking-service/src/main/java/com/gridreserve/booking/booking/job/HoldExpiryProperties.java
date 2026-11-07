package com.gridreserve.booking.booking.job;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code gridreserve.holds.expiry.*}: the background sweep (FR-EXP-01, A-05). The defaults live here, so
 * the config repo needs entries only to change them. Read at startup; not refreshable.
 */
@Validated
@ConfigurationProperties("gridreserve.holds.expiry")
public record HoldExpiryProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("30s") @NotNull Duration interval,
        @DefaultValue("100") @Min(1) @Max(1000) int batchSize) {
}
