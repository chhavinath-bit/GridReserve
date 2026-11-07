package com.gridreserve.booking.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.common.CompositeCustomizer;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadProperties;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerProperties;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Makes the resilience settings refreshable (PLAT-05). After a refresh (scripts/refresh-config.sh →
 * POST /actuator/refresh), Spring Cloud re-reads the configuration into Resilience4j's settings objects,
 * but on Boot 4 Resilience4j does not rebuild the circuit breakers, retries and bulkheads that already
 * exist (checked on the Compose stack in M1.9). This listener does: for every configured policy it builds
 * the new config and swaps it into the registry. Callers look policies up on every call
 * ({@code IdentityGateway}), so the next call uses the new values. A swapped circuit breaker starts CLOSED.
 */
@Component
class ResilienceRefresher {

    private static final Logger log = LoggerFactory.getLogger(ResilienceRefresher.class);

    private final CircuitBreakerProperties circuitBreakerProperties;
    private final RetryProperties retryProperties;
    private final BulkheadProperties bulkheadProperties;
    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;
    private final BulkheadRegistry bulkheads;

    ResilienceRefresher(CircuitBreakerProperties circuitBreakerProperties, RetryProperties retryProperties,
                        BulkheadProperties bulkheadProperties, CircuitBreakerRegistry circuitBreakers,
                        RetryRegistry retries, BulkheadRegistry bulkheads) {
        this.circuitBreakerProperties = circuitBreakerProperties;
        this.retryProperties = retryProperties;
        this.bulkheadProperties = bulkheadProperties;
        this.circuitBreakers = circuitBreakers;
        this.retries = retries;
        this.bulkheads = bulkheads;
    }

    /** Runs after the refreshed values are bound (Spring Cloud publishes this event last). */
    @EventListener(RefreshScopeRefreshedEvent.class)
    void rebuild() {
        circuitBreakerProperties.getInstances().forEach((name, settings) -> circuitBreakers.replace(name,
                CircuitBreaker.of(name, circuitBreakerProperties.createCircuitBreakerConfig(name, settings,
                        new CompositeCustomizer<>(List.of())))));
        retryProperties.getInstances().keySet().forEach(name -> retries.replace(name,
                Retry.of(name, retryProperties.createRetryConfig(name, new CompositeCustomizer<>(List.of())))));
        bulkheadProperties.getInstances().forEach((name, settings) -> bulkheads.replace(name,
                Bulkhead.of(name, bulkheadProperties.createBulkheadConfig(settings, new CompositeCustomizer<>(List.of()), name))));
        log.info("Resilience policies rebuilt after a configuration refresh: circuit breakers {}, retries {}, bulkheads {}",
                circuitBreakerProperties.getInstances().keySet(), retryProperties.getInstances().keySet(),
                bulkheadProperties.getInstances().keySet());
    }
}
