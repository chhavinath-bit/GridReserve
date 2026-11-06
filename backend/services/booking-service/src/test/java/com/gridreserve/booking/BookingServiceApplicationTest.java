package com.gridreserve.booking;

import com.gridreserve.booking.support.ServiceIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Starts the service (with its PostgreSQL Testcontainer) and calls it over real HTTP. */
@ServiceIntegrationTest
class BookingServiceApplicationTest {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("M1.1: the service starts and answers the ping")
    void pingAnswers() throws Exception {
        HttpResponse<String> response = get("/api/v1/ping");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"service\":\"booking-service\"");
    }

    @Test
    @DisplayName("API-05/PLAT-12: an unknown path returns the standard error JSON with a request ID (common-web is active)")
    void unknownPathUsesStandardFormat() throws Exception {
        HttpResponse<String> response = get("/api/v1/does-not-exist");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
        assertThat(response.body())
                .contains("\"code\":\"NOT_FOUND\"")
                .containsPattern("\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T[^\"]+Z\"");
    }
}
