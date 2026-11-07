package com.gridreserve.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Turns on Spring Framework 7's {@code @Retryable} (D-11): built into spring-context, so no extra
 * library. Used only by {@code BookingService} to rerun a transaction PostgreSQL killed to break a deadlock.
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
public class RetryConfig {
}
