package com.gridreserve.booking.booking.job;

import com.gridreserve.booking.booking.service.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Every {@code interval} (1 hour), deletes idempotency keys older than 24 hours (IDEM-07). Housekeeping only:
 * an expired key already counts as unused before it is deleted, so correctness never waits for this job
 * (FR-EXP-04's rule for every job). One DELETE; several instances running it at once is harmless.
 */
@Component
@EnableConfigurationProperties(IdempotencyCleanupProperties.class)
public class IdempotencyCleanupJob implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final IdempotencyService idempotency;
    private final IdempotencyCleanupProperties properties;

    public IdempotencyCleanupJob(IdempotencyService idempotency, IdempotencyCleanupProperties properties) {
        this.idempotency = idempotency;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (properties.enabled()) {
            registrar.addFixedDelayTask(this::run, properties.interval());
        }
    }

    public int run() {
        int deleted = idempotency.deleteExpired();
        if (deleted > 0) {
            log.info("Idempotency cleanup: {} keys older than 24 hours deleted", deleted);
        }
        return deleted;
    }
}
