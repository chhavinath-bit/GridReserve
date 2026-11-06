package com.gridreserve.identity;

import com.gridreserve.identity.support.ServiceIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Starts the service without Config Server or Eureka and calls it over real HTTP. */
@ServiceIntegrationTest
class IdentityServiceApplicationTest {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return get(path, null);
    }

    private HttpResponse<String> get(String path, String bearerToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String demoToken() throws Exception {
        HttpRequest login = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"alice@gridreserve.test\",\"password\":\"demo-alice-pass\"}"))
                .build();
        String body = http.send(login, HttpResponse.BodyHandlers.ofString()).body();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    @Test
    @DisplayName("M1.1: the service starts and answers the ping")
    void pingAnswers() throws Exception {
        HttpResponse<String> response = get("/api/v1/auth/ping");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"service\":\"identity-service\"");
    }

    @Test
    @DisplayName("API-05/PLAT-12: an unknown path returns the standard error JSON with a request ID (common-web is active)")
    void unknownPathUsesStandardFormat() throws Exception {
        // Signed in: since M1.3a every path except the public ones needs a token first (else 401).
        HttpResponse<String> response = get("/api/v1/does-not-exist", demoToken());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
        assertThat(response.body())
                .contains("\"code\":\"NOT_FOUND\"")
                .containsPattern("\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T[^\"]+Z\"");
    }
}
