package com.gridreserve.gateway.security;

import com.gridreserve.common.web.security.AccessTokenVerification;
import com.gridreserve.gateway.error.GatewayErrorResponse;
import com.gridreserve.gateway.request.RequestIdWebFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * The reactive twin of common-web's {@code JsonSecurityErrorHandler}: security failures at the
 * gateway in the standard error JSON (API-05), with the same codes as the services, so the client
 * cannot tell, and need not care, which one refused it. The reason a token was rejected is never sent.
 */
@Component
class GatewaySecurityErrorHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final JsonMapper jsonMapper;
    private final Clock clock;

    GatewaySecurityErrorHandler(JsonMapper jsonMapper, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        if (AccessTokenVerification.isExpired(ex)) {
            return write(exchange, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Your session has expired.");
        }
        return write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in to continue.");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
        return write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do this.");
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        GatewayErrorResponse body = new GatewayErrorResponse(clock.instant(), status.value(), code, message,
                exchange.getRequest().getPath().value(), RequestIdWebFilter.requestId(exchange));
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(jsonMapper.writeValueAsBytes(body));
        return response.writeWith(Mono.just(buffer));
    }
}
