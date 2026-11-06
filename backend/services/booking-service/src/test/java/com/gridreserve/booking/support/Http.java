package com.gridreserve.booking.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** A tiny JSON-over-HTTP client for tests that call the running service like a real client. */
public final class Http {

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseUrl;

    public Http(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public Response get(String path, String bearerToken) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body().isEmpty() ? null : json.readTree(response.body());
            return new Response(response.statusCode(), body);
        } catch (Exception ex) {
            throw new IllegalStateException("HTTP call failed: " + path, ex);
        }
    }

    public record Response(int status, JsonNode body) {

        public String code() {
            return body.get("code").asString();
        }
    }
}
