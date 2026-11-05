package com.gridreserve.identity.ping.dto;

/** Temporary M1.1 response: which service and which instance answered (shows load balancing). */
public record PingResponse(String service, String instance) {
}
