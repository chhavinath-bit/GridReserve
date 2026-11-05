package com.gridreserve.booking.client;

import com.gridreserve.booking.client.dto.IdentityPing;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * M1.1 spike (S1/S2): a Feign client resolved through Eureka by service ID and load-balanced by
 * Spring Cloud LoadBalancer. With spring.cloud.openfeign.circuitbreaker.enabled=true (config repo)
 * every call runs through a Resilience4j circuit breaker and falls back when identity is down.
 */
@FeignClient(name = "identity-service", fallback = IdentityClientFallback.class)
public interface IdentityClient {

    @GetMapping("/internal/ping")
    IdentityPing ping();
}
