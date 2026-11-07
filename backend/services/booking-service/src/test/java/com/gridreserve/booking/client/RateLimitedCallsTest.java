package com.gridreserve.booking.client;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitedCallsTest {

    // The A-25 default from the config repo: 20 calls per second, no waiting.
    private static final RateLimiterConfig DEFAULT = RateLimiterConfig.custom()
            .limitForPeriod(20)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ZERO)
            .build();

    @Test
    @DisplayName("RES-05: a burst of 25 calls within one second → 20 reach the downstream, 5 get the fallback at once")
    void limitsABurst() {
        RateLimitedCalls calls = new RateLimitedCalls(RateLimiterRegistry.of(DEFAULT));
        AtomicInteger reached = new AtomicInteger();

        long fallbacks = 0;
        for (int call = 0; call < 25; call++) {
            String result = calls.call("payment-provider", () -> {
                reached.incrementAndGet();
                return "charged";
            }, () -> "later");
            if (result.equals("later")) {
                fallbacks++;
            }
        }

        assertThat(reached).hasValue(20);
        assertThat(fallbacks).isEqualTo(5);
    }

    @Test
    @DisplayName("RES-05: each downstream has its own limit")
    void limitsPerDownstream() {
        RateLimitedCalls calls = new RateLimitedCalls(RateLimiterRegistry.of(DEFAULT));
        for (int call = 0; call < 20; call++) {
            calls.call("payment-provider", () -> "ok", () -> "later");
        }

        assertThat(calls.call("payment-provider", () -> "ok", () -> "later")).isEqualTo("later");
        assertThat(calls.call("another-downstream", () -> "ok", () -> "later")).isEqualTo("ok");
    }
}
