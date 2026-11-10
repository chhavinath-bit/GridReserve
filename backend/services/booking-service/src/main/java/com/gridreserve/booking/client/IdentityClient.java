package com.gridreserve.booking.client;

import com.gridreserve.booking.client.dto.IdentityPing;
import com.gridreserve.booking.client.dto.IdentityUser;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * identity-service's internal API, resolved through Eureka and load-balanced (lb://identity-service).
 * Only {@link IdentityGateway} calls this: it adds retry, circuit breaker, bulkhead and the fallback
 * (ARCHITECTURE §11). Timeouts come from config ({@code spring.cloud.openfeign.client.config.default}:
 * 1 s connect, 2 s read, RES-01; per-client settings would be keyed by context ID, which is the service
 * name here). Feign's own retryer stays off, so retries are never doubled.
 */
@FeignClient(name = "identity-service", configuration = {IdentityErrors.class, ServiceAuthentication.class})
public interface IdentityClient {

    @GetMapping("/internal/ping")
    IdentityPing ping();

    @GetMapping("/internal/users")
    List<IdentityUser> users(@RequestParam("ids") List<Long> ids);
}
