package com.gridreserve.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on scheduled jobs. The scheduler's pool (2 threads named {@code gr-sched-*}) and its 20-second
 * graceful shutdown are set in application.yml (spring.task.scheduling.*), so they hold everywhere.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
