package com.gridreserve.gateway.error;

import com.gridreserve.gateway.request.RequestIdWebFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.concurrent.TimeoutException;

/**
 * Writes gateway-level errors (no route, upstream timeout, unexpected failures) in the standard
 * JSON format, so clients see one error shape whether the gateway or a service answered.
 * Order -2 puts it ahead of Spring Boot's default handler.
 */
@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    private final JsonMapper jsonMapper;
    private final Clock clock;

    public GatewayErrorHandler(JsonMapper jsonMapper, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatusCode status;
        String code;
        String message;
        if (ex instanceof ResponseStatusException rse && rse.getStatusCode().value() == 404) {
            status = HttpStatus.NOT_FOUND;
            code = "NOT_FOUND";
            message = "Nothing exists at this path.";
        } else if (ex instanceof ResponseStatusException rse && rse.getStatusCode().value() == 504
                || ex instanceof TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            code = "UPSTREAM_TIMEOUT";
            message = "The service took too long to answer. Please retry.";
        } else if (ex instanceof ResponseStatusException rse && rse.getStatusCode().value() == 503) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = "SERVICE_UNAVAILABLE";
            message = "This part of GridReserve is temporarily unavailable. Please retry shortly.";
        } else {
            log.error("Unexpected gateway error on {}", exchange.getRequest().getPath(), ex);
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "INTERNAL_ERROR";
            message = "Something went wrong. Please try again.";
        }

        GatewayErrorResponse body = new GatewayErrorResponse(clock.instant(), status.value(), code, message,
                exchange.getRequest().getPath().value(), RequestIdWebFilter.requestId(exchange));
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(jsonMapper.writeValueAsBytes(body));
        return response.writeWith(Mono.just(buffer));
    }
}
