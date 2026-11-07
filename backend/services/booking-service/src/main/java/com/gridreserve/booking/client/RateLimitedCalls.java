package com.gridreserve.booking.client;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Per-instance rate limit for calls to a rate-sensitive downstream (RES-05): at most N calls per period
 * from this instance (default 20 per second, config repo), extra calls get the fallback at once instead of
 * waiting. It protects the downstream; limiting clients is a separate, distributed job (Redis at the
 * gateway, Phase 4). No caller yet: the first is the mock payment provider in Phase 5. Wraps inside the
 * other policies (CLAUDE.md §9 order: Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead).
 */
@Component
public class RateLimitedCalls {

    private static final Logger log = LoggerFactory.getLogger(RateLimitedCalls.class);

    private final RateLimiterRegistry limiters;

    public RateLimitedCalls(RateLimiterRegistry limiters) {
        this.limiters = limiters;
    }

    public <T> T call(String downstream, Supplier<T> call, Supplier<T> fallback) {
        RateLimiter limiter = limiters.rateLimiter(downstream);
        try {
            return RateLimiter.decorateSupplier(limiter, call).get();
        } catch (RequestNotPermitted limited) {
            log.warn("Rate limit reached for {}; using the fallback", downstream);
            return fallback.get();
        }
    }
}
