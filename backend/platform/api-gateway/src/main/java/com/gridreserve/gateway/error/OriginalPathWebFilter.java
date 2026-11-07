package com.gridreserve.gateway.error;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Remembers the path the client called before routing changes it. A circuit-breaker fallback is a
 * forward to /fallback/{service}; without this, its error body would show that internal path instead of
 * the one the client asked for (API-05).
 */
@Component
public class OriginalPathWebFilter implements WebFilter, Ordered {

    static final String ATTRIBUTE = "gridreserve.originalPath";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getAttributes().putIfAbsent(ATTRIBUTE, exchange.getRequest().getPath().value());
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    static String originalPath(ServerWebExchange exchange) {
        Object path = exchange.getAttribute(ATTRIBUTE);
        return path != null ? path.toString() : exchange.getRequest().getPath().value();
    }
}
