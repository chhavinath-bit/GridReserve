package com.gridreserve.booking.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RES-08: resilience state in metrics and health. Circuit-breaker and bulkhead metrics are already
 * published by Spring Cloud CircuitBreaker; Resilience4j's own Spring Boot module targets Boot 3, so on
 * Boot 4 its retry metrics and health indicator don't switch on. These two beans fill exactly that gap.
 */
@Configuration(proxyBeanMethods = false)
class ResilienceObservabilityConfig {

    /** resilience4j.retry.calls{name,kind}: successful/failed with and without retry. */
    @Bean
    TaggedRetryMetrics retryMetrics(RetryRegistry retries, MeterRegistry meters) {
        TaggedRetryMetrics metrics = TaggedRetryMetrics.ofRetryRegistry(retries);
        metrics.bindTo(meters);
        return metrics;
    }

    /**
     * /actuator/health component "circuitBreakers": state, failure rate and threshold per circuit, for
     * admins (show-details when-authorized). Always UP: an open circuit to identity-service is something
     * booking-service handles with a fallback (RES-07), not a reason to take it out of the load balancer.
     */
    @Bean
    HealthIndicator circuitBreakers(CircuitBreakerRegistry circuitBreakers) {
        return () -> {
            Map<String, Object> details = new LinkedHashMap<>();
            for (CircuitBreaker breaker : circuitBreakers.getAllCircuitBreakers()) {
                details.put(breaker.getName(), Map.of(
                        "state", breaker.getState().name(),
                        "failureRate", breaker.getMetrics().getFailureRate(),
                        "failureRateThreshold", breaker.getCircuitBreakerConfig().getFailureRateThreshold(),
                        "notPermittedCalls", breaker.getMetrics().getNumberOfNotPermittedCalls()));
            }
            return Health.up().withDetails(details).build();
        };
    }
}
