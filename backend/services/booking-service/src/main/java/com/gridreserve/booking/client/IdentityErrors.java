package com.gridreserve.booking.client;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * Sorts identity-service's error answers (ARCHITECTURE §11): a 4xx means our request was wrong and the
 * same call would fail again, so it is never retried and doesn't count against the circuit; a 5xx means
 * identity is in trouble, so it may be retried and counts. Network errors and timeouts reach the adapter
 * as Feign's own RetryableException, also counted. Not a @Configuration: it applies only to the clients
 * that name it.
 */
class IdentityErrors {

    @Bean
    ErrorDecoder identityErrorDecoder() {
        return (methodKey, response) -> response.status() >= 400 && response.status() < 500
                ? new IdentityRejectedException(methodKey + " answered " + response.status())
                : new IdentityUnavailableException(methodKey + " answered " + response.status());
    }

    /** identity-service refused the request (4xx): not retried, not a sign identity is down. */
    public static class IdentityRejectedException extends RuntimeException {
        public IdentityRejectedException(String message) {
            super(message);
        }
    }

    /** identity-service failed (5xx): retried, counts toward opening the circuit. */
    public static class IdentityUnavailableException extends RuntimeException {
        public IdentityUnavailableException(String message) {
            super(message);
        }
    }
}
