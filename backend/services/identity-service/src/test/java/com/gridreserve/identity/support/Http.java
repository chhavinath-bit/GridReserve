package com.gridreserve.identity.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

/**
 * A tiny JSON-over-HTTP client for tests that call the running service like a real client.
 * It keeps no cookies: each test passes the cookie it wants, so nothing leaks between calls.
 */
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
        return send(request.build());
    }

    public Response post(String path, String jsonBody) {
        return post(path, jsonBody, Map.of());
    }

    /** A POST with extra headers, e.g. {@code Cookie}, {@code Origin}. An empty body sends no content type. */
    public Response post(String path, String jsonBody, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (jsonBody.isEmpty()) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        }
        headers.forEach(request::header);
        return send(request.build());
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body().isEmpty() ? null : json.readTree(response.body());
            return new Response(response.statusCode(), body, response);
        } catch (Exception ex) {
            throw new IllegalStateException("HTTP call failed: " + request.uri(), ex);
        }
    }

    public record Response(int status, JsonNode body, HttpResponse<String> raw) {

        public String code() {
            return body.get("code").asString();
        }

        /** The full {@code Set-Cookie} header for the named cookie, if the response set it. */
        public Optional<String> setCookie(String name) {
            return raw.headers().allValues("Set-Cookie").stream()
                    .filter(header -> header.startsWith(name + "="))
                    .findFirst();
        }

        /** Just the value of the named cookie from {@code Set-Cookie}. */
        public String cookieValue(String name) {
            String header = setCookie(name).orElseThrow(() -> new AssertionError("no Set-Cookie for " + name));
            return header.substring(name.length() + 1, header.indexOf(';'));
        }
    }
}
