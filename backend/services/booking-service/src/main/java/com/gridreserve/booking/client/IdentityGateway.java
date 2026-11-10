package com.gridreserve.booking.client;

import com.gridreserve.booking.client.dto.IdentityPing;
import com.gridreserve.booking.client.dto.IdentityUser;
import com.gridreserve.booking.client.dto.MemberDirectory;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The only way booking-service calls identity-service (ARCHITECTURE §11): the Feign client wrapped in
 * Resilience4j policies named {@code identity-service}, configured in the config repo (A-25), with an
 * explicit fallback for every call (RES-06). Order, outermost first (CLAUDE.md §9):
 * <pre>Retry → CircuitBreaker → Bulkhead → Feign call (timeouts)</pre>
 * so one retry attempt is one circuit-breaker call, and a bulkhead rejection fails fast without waiting.
 * Policies are looked up in their registries on every call, not kept, so a configuration refresh can
 * replace them. Never called inside a database transaction (CLAUDE.md §6.2).
 */
@Component
public class IdentityGateway {

    public static final String POLICY = "identity-service";

    private static final Logger log = LoggerFactory.getLogger(IdentityGateway.class);

    private final IdentityClient client;
    private final RetryRegistry retries;
    private final CircuitBreakerRegistry circuitBreakers;
    private final BulkheadRegistry bulkheads;

    public IdentityGateway(IdentityClient client, RetryRegistry retries, CircuitBreakerRegistry circuitBreakers,
                           BulkheadRegistry bulkheads) {
        this.client = client;
        this.retries = retries;
        this.circuitBreakers = circuitBreakers;
        this.bulkheads = bulkheads;
    }

    /**
     * Emails and names for these members (FR-ADM-01). Read-only, so retried; if identity-service can't
     * answer, the fallback is "IDs only" (FS-33).
     */
    public MemberDirectory members(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return new MemberDirectory(true, Map.of());
        }
        List<Long> ids = List.copyOf(memberIds);
        return protect(() -> {
            Map<Long, IdentityUser> found = client.users(ids).stream()
                    .collect(Collectors.toMap(IdentityUser::id, Function.identity(), (first, second) -> first));
            return new MemberDirectory(true, found);
        }, MemberDirectory.unavailable(), "members");
    }

    /** M1.1 spike kept as a quick end-to-end check of service-to-service calls. */
    public IdentityPing ping() {
        return protect(client::ping, new IdentityPing("identity-service", "unavailable (fallback)"), "ping");
    }

    private <T> T protect(Supplier<T> call, T fallback, String operation) {
        Bulkhead bulkhead = bulkheads.bulkhead(POLICY);
        CircuitBreaker circuitBreaker = circuitBreakers.circuitBreaker(POLICY);
        Retry retry = retries.retry(POLICY);
        Supplier<T> protectedCall = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker,
                        Bulkhead.decorateSupplier(bulkhead, call)));
        try {
            return protectedCall.get();
        } catch (RuntimeException ex) {
            // Not swallowed silently: every fallback is logged with the reason (CLAUDE.md §9).
            log.warn("identity-service {} failed ({}); using the fallback", operation, ex.getClass().getSimpleName());
            return fallback;
        }
    }
}
