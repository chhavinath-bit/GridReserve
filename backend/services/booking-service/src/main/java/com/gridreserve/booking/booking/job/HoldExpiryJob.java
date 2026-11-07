package com.gridreserve.booking.booking.job;

import com.gridreserve.booking.booking.service.HoldExpiryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Third expiry layer (ARCHITECTURE §9): every {@code interval}, mark run-out holds EXPIRED. It is a
 * safety net, not the guard: confirm, cancel and new holds already treat run-out holds as gone
 * (FR-EXP-04). Fixed delay, so a slow run never overlaps the next one. Safe on any number of instances
 * (see {@link HoldExpiryService#expire}).
 */
@Component
@EnableConfigurationProperties(HoldExpiryProperties.class)
public class HoldExpiryJob implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final HoldExpiryService expiry;
    private final HoldExpiryProperties properties;

    public HoldExpiryJob(HoldExpiryService expiry, HoldExpiryProperties properties) {
        this.expiry = expiry;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (properties.enabled()) {
            registrar.addFixedDelayTask(this::run, properties.interval());
        }
    }

    /** One sweep: up to batch-size holds, each in its own transaction. Returns how many this run expired. */
    public int run() {
        int expired = 0;
        for (long bookingId : expiry.runOutHolds(properties.batchSize())) {
            if (Thread.currentThread().isInterrupted()) {
                break; // shutting down: every finished row is already committed
            }
            if (expiry.expire(bookingId)) {
                expired++;
            }
        }
        if (expired > 0) {
            log.info("Hold expiry: {} run-out holds marked EXPIRED", expired);
        }
        return expired;
    }
}
