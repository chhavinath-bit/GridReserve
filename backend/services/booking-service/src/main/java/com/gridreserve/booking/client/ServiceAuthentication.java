package com.gridreserve.booking.client;

import com.gridreserve.common.web.request.RequestIdFilter;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;

/**
 * Adds a service token (D-19) and the current request ID to every call of the client that names this
 * class, so identity-service can check who is calling and the logs of both services line up.
 */
class ServiceAuthentication {

    @Bean
    RequestInterceptor serviceTokenInterceptor(ServiceTokens tokens) {
        return template -> {
            template.header("Authorization", "Bearer " + tokens.current());
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                template.header(RequestIdFilter.HEADER, requestId);
            }
        };
    }
}
