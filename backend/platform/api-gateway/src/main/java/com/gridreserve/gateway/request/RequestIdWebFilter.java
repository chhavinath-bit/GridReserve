package com.gridreserve.gateway.request;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Starts the request-ID chain (PLAT-09): every request leaving the gateway carries an
 * {@code X-Request-Id}, which the services log and return. A WebFilter (not a gateway GlobalFilter)
 * so that unrouted requests, such as 404s, get an ID too.
 */
@Component
public class RequestIdWebFilter implements WebFilter, Ordered {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = "requestId";

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String requestId = incoming != null && VALID_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER, requestId))
                .build();
        ServerWebExchange withId = exchange.mutate().request(request).build();
        withId.getAttributes().put(ATTRIBUTE, requestId);
        // Routed responses already carry the service's X-Request-Id (same value); add it only when
        // missing, e.g. for the gateway's own 404s and fallbacks, so the header never appears twice.
        withId.getResponse().beforeCommit(() -> {
            if (!withId.getResponse().getHeaders().containsHeader(HEADER)) {
                withId.getResponse().getHeaders().set(HEADER, requestId);
            }
            return Mono.empty();
        });
        return chain.filter(withId);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    public static String requestId(ServerWebExchange exchange) {
        Object id = exchange.getAttribute(ATTRIBUTE);
        return id != null ? id.toString() : null;
    }
}
