package com.gridreserve.booking.booking.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * {@code gridreserve.holds.expired{layer}} (ARCHITECTURE §17): which of the three expiry layers actually
 * marks run-out holds EXPIRED. Mostly "job" means the sweep does its work; many "hold" or "confirm" means
 * the job lags. Correctness never depends on the job either way (FR-EXP-04).
 */
@Component
public class HoldExpiryMetrics {

    public enum Layer { CONFIRM, CANCEL, HOLD, JOB }

    private final MeterRegistry registry;

    public HoldExpiryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void expired(Layer layer, int count) {
        if (count > 0) {
            counter(layer).increment(count);
        }
    }

    public Counter counter(Layer layer) {
        return Counter.builder("gridreserve.holds.expired")
                .description("Run-out holds marked EXPIRED, by the layer that did it")
                .tag("layer", layer.name().toLowerCase())
                .register(registry);
    }
}
