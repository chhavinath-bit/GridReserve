package com.gridreserve.booking.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/** A tiny JSON-over-HTTP client for tests that call the running service like a real client. */
public final class Http {

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseUrl;

    public Http(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public Response get(String path, String bearerToken) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET(), path, bearerToken);
    }

    /** POSTs {@code body} as JSON ({@code null} = no body). */
    public Response post(String path, String bearerToken, Object body) {
        return post(path, bearerToken, body, Map.of());
    }

    /** POSTs {@code body} (an object as JSON, or a String sent as it is) with extra headers. */
    public Response post(String path, String bearerToken, Object body, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
        headers.forEach(request::header);
        if (body == null) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            String text = body instanceof String raw ? raw : json.writeValueAsString(body);
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(text));
        }
        return send(request, path, bearerToken);
    }

    /** PUTs {@code body} as JSON. */
    public Response put(String path, String bearerToken, Object body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return send(request, path, bearerToken);
    }

    private Response send(HttpRequest.Builder request, String path, String bearerToken) {
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body().isEmpty() ? null : json.readTree(response.body());
            return new Response(response.statusCode(), body, response.headers().firstValue("Location").orElse(null),
                    response.headers().map());
        } catch (Exception ex) {
            throw new IllegalStateException("HTTP call failed: " + path, ex);
        }
    }

    public record Response(int status, JsonNode body, String location, Map<String, List<String>> headers) {

        public String code() {
            return body.get("code").asString();
        }

        /** A response header's first value, or null (header names are case-insensitive). */
        public String header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst()).findFirst().orElse(null);
        }
    }
}
