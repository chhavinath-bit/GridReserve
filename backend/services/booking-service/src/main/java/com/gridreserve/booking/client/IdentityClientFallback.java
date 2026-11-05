package com.gridreserve.booking.client;

import com.gridreserve.booking.client.dto.IdentityPing;
import org.springframework.stereotype.Component;

/** Used when identity-service is unreachable, slow or its circuit is open (RES-06). */
@Component
public class IdentityClientFallback implements IdentityClient {

    @Override
    public IdentityPing ping() {
        return new IdentityPing("identity-service", "unavailable (fallback)");
    }
}
