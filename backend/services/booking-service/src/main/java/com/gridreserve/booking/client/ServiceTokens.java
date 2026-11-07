package com.gridreserve.booking.client;

import com.gridreserve.booking.client.dto.ServiceToken;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps this service's current service token and fetches a new one shortly before it expires. If two
 * threads find it stale at once, both fetch and the last one wins: harmless, and no lock is needed. If
 * identity-service is down the fetch fails, and so would the call it is for; the adapter's fallback
 * handles both the same way.
 */
@Component
@EnableConfigurationProperties(ServiceClientProperties.class)
public class ServiceTokens {

    // Renew a little early, so a token never expires in flight.
    private static final Duration SAFETY_MARGIN = Duration.ofSeconds(30);

    private record Cached(String token, Instant renewAfter) {
    }

    private final IdentityTokenClient client;
    private final ServiceClientProperties credentials;
    private final Clock clock;
    private final AtomicReference<Cached> cached = new AtomicReference<>();

    public ServiceTokens(IdentityTokenClient client, ServiceClientProperties credentials, Clock clock) {
        this.client = client;
        this.credentials = credentials;
        this.clock = clock;
    }

    public String current() {
        Cached current = cached.get();
        Instant now = clock.instant();
        if (current != null && now.isBefore(current.renewAfter())) {
            return current.token();
        }
        String basic = Base64.getEncoder().encodeToString(
                (credentials.id() + ":" + credentials.secret()).getBytes(StandardCharsets.UTF_8));
        ServiceToken fresh = client.serviceToken("Basic " + basic);
        Instant renewAfter = now.plusSeconds(fresh.expiresIn()).minus(SAFETY_MARGIN);
        cached.set(new Cached(fresh.accessToken(), renewAfter));
        return fresh.accessToken();
    }

    /** Drops the cached token, so the next call fetches a new one (tests). */
    void forget() {
        cached.set(null);
    }
}
