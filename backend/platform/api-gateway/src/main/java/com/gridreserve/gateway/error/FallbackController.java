package com.gridreserve.gateway.error;

import com.gridreserve.gateway.request.RequestIdWebFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.time.Clock;

/**
 * Target of the routes' CircuitBreaker filter (fallbackUri: forward:/fallback/{service}). The client gets
 * the standard error format instead of a raw connection error (RES-06): {@code 504 UPSTREAM_TIMEOUT} when
 * the service was too slow (RES-01), {@code 503 SERVICE_UNAVAILABLE} when it failed, was unreachable, or
 * its circuit is open. The cause is the exception the circuit breaker saw; the path is the one the client
 * called, not this forward.
 */
@RestController
public class FallbackController {

    private final Clock clock;

    public FallbackController(Clock clock) {
        this.clock = clock;
    }

    @RequestMapping("/fallback/{service}")
    ResponseEntity<GatewayErrorResponse> fallback(@PathVariable String service, ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        boolean timedOut = isTimeout(cause);
        HttpStatus status = timedOut ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        GatewayErrorResponse body = new GatewayErrorResponse(clock.instant(), status.value(),
                timedOut ? "UPSTREAM_TIMEOUT" : "SERVICE_UNAVAILABLE",
                timedOut ? "This took too long. Please retry shortly."
                        : "This part of GridReserve is temporarily unavailable. Please retry shortly.",
                OriginalPathWebFilter.originalPath(exchange), RequestIdWebFilter.requestId(exchange));
        return ResponseEntity.status(status).body(body);
    }

    // The time limiter throws java.util.concurrent.TimeoutException; the HTTP client's response timeout
    // surfaces as a 504 ResponseStatusException or the gateway's own TimeoutException. Any of them, anywhere
    // in the cause chain, means "too slow".
    private static boolean isTimeout(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current.getClass().getSimpleName().equals("TimeoutException")
                    || current instanceof ResponseStatusException status && status.getStatusCode().value() == 504) {
                return true;
            }
        }
        return false;
    }
}
