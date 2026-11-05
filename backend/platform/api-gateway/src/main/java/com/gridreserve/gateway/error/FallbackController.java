package com.gridreserve.gateway.error;

import com.gridreserve.gateway.request.RequestIdWebFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.Clock;

/**
 * Target of the routes' CircuitBreaker filter (fallbackUri: forward:/fallback/{service}).
 * When a service is down or its circuit is open, the client gets a clear 503 in the standard
 * error format instead of a raw connection error (RES-06).
 */
@RestController
public class FallbackController {

    private final Clock clock;

    public FallbackController(Clock clock) {
        this.clock = clock;
    }

    @RequestMapping("/fallback/{service}")
    ResponseEntity<GatewayErrorResponse> fallback(@PathVariable String service, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        GatewayErrorResponse body = new GatewayErrorResponse(clock.instant(), status.value(),
                "SERVICE_UNAVAILABLE", "This part of GridReserve is temporarily unavailable. Please retry shortly.",
                exchange.getRequest().getPath().value(), RequestIdWebFilter.requestId(exchange));
        return ResponseEntity.status(status).body(body);
    }
}
