package com.gridreserve.systemtests;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The running Compose stack as a client sees it: HTTP through the gateway and Eureka, plus a few
 * {@code docker compose} commands for what clients can't see (database rows, per-instance metrics).
 * Addresses come from system properties so CI and a laptop can differ; defaults fit the README setup.
 */
final class Stack {

    static final String GATEWAY = System.getProperty("gridreserve.gateway", "http://localhost:8080");
    static final String EUREKA = System.getProperty("gridreserve.eureka", "http://localhost:8761");
    static final File INFRA_DIR = new File(System.getProperty("gridreserve.infra.dir", "../../infra"));

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Stack() {
    }

    record Response(int status, JsonNode body) {

        String code() {
            return body == null || body.get("code") == null ? "HTTP_" + status : body.get("code").asString();
        }
    }

    static Response get(String url, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return send(request.GET());
    }

    static Response post(String url, String token, Object body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return send(request.POST(body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
    }

    private static Response send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = HTTP.send(request.timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body().isBlank() ? null : JSON.readTree(response.body());
            return new Response(response.statusCode(), body);
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("Request failed: " + request.build().uri(), ex);
        }
    }

    /**
     * Waits until the stack is really usable, not just healthy: Eureka lists {@code bookingInstances}
     * booking-service instances UP, and the gateway routes to booking-service (public ping → 200) and to
     * identity-service (a wrong login → 401, not 503). Containers report healthy before the gateway has
     * fetched them from Eureka, which took 15 to 50 s in M1.6/M1.7. Polls once a second, up to 3 minutes.
     */
    static void awaitReady(int bookingInstances) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
        String last = "not checked yet";
        while (System.nanoTime() < deadline) {
            try {
                long up = upInstances("BOOKING-SERVICE");
                int ping = get(GATEWAY + "/api/v1/ping", null).status();
                int login = post(GATEWAY + "/api/v1/auth/login", null,
                        java.util.Map.of("email", "nobody@gridreserve.test", "password", "not-a-password")).status();
                if (up == bookingInstances && ping == 200 && login == 401) {
                    return;
                }
                last = "booking instances UP=" + up + ", ping=" + ping + ", login=" + login;
            } catch (RuntimeException notYet) {
                last = notYet.getMessage();
            }
            Thread.sleep(1_000);
        }
        throw new IllegalStateException("Stack not ready after 3 minutes: " + last);
    }

    /**
     * Waits until an authenticated catalog read through the gateway answers 200 several times in a row, so round
     * robin has reached every booking instance and each has served (and warmed up) a real query. {@link
     * #awaitReady} only proves the instances are registered and the ping answers; on a slow CI runner the
     * first catalog query of a fresh instance could still fail (PR #18, CI run 42893725631).
     */
    static void awaitCatalogReadable(String token, int bookingInstances) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        int inARow = 0;
        String last = "not checked yet";
        while (System.nanoTime() < deadline) {
            Response venues = get(GATEWAY + "/api/v1/venues?size=100", token);
            if (venues.status() == 200) {
                if (++inARow >= 2 * bookingInstances) {
                    return;
                }
                continue;
            }
            inARow = 0;
            last = venues.status() + " " + venues.body();
            Thread.sleep(500);
        }
        throw new IllegalStateException("Catalog not readable through the gateway after 2 minutes: " + last);
    }

    /**
     * Waits until Eureka's UP booking-service instances are exactly the running containers (same IPs). After
     * a container is killed and replaced, Eureka briefly still lists the dead one and not yet the new one.
     */
    static void awaitBookingRegistryInSync() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
        while (System.nanoTime() < deadline) {
            java.util.Set<String> running = new java.util.HashSet<>();
            for (String id : compose("ps", "-q", "booking-service").lines().toList()) {
                running.add(docker("inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", id));
            }
            java.util.Set<String> registered = new java.util.HashSet<>();
            Response response = get(EUREKA + "/eureka/apps/BOOKING-SERVICE", null);
            if (response.status() == 200) {
                JsonNode instance = response.body().get("application").get("instance");
                (instance.isArray() ? instance.valueStream().toList() : java.util.List.of(instance)).stream()
                        .filter(each -> each.get("status").asString().equals("UP"))
                        .forEach(each -> registered.add(each.get("ipAddr").asString()));
            }
            if (!running.isEmpty() && running.equals(registered)) {
                return;
            }
            Thread.sleep(1_000);
        }
        throw new IllegalStateException("Eureka never matched the running booking-service containers");
    }

    private static String docker(String... args) {
        List<String> full = new ArrayList<>(List.of("docker"));
        full.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(full).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            process.waitFor(30, TimeUnit.SECONDS);
            return output;
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("docker " + args[0] + " failed", ex);
        }
    }

    static long upInstances(String application) {
        Response response = get(EUREKA + "/eureka/apps/" + application, null);
        if (response.status() != 200) {
            return 0;
        }
        JsonNode instance = response.body().get("application").get("instance");
        // Eureka sends an object for one instance and an array for several.
        java.util.List<JsonNode> instances = instance.isArray() ? instance.valueStream().toList() : java.util.List.of(instance);
        return instances.stream().filter(each -> each.get("status").asString().equals("UP")).count();
    }

    /** Runs a command in a Compose service container and returns its standard output. */
    static String composeExec(String service, int index, String... command) {
        List<String> full = new ArrayList<>(List.of("docker", "compose", "exec", "-T", "--index", String.valueOf(index),
                service));
        full.addAll(List.of(command));
        try {
            Process process = new ProcessBuilder(full).directory(INFRA_DIR).redirectErrorStream(false).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("Command failed: " + String.join(" ", full.subList(0, 7)));
            }
            return output;
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("Could not run docker compose in " + INFRA_DIR, ex);
        }
    }

    /** Runs {@code docker compose <args>} in infra/ (stop, start, kill, up …) and returns its output. */
    static String compose(String... args) {
        List<String> full = new ArrayList<>(List.of("docker", "compose"));
        full.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(full).directory(INFRA_DIR).redirectErrorStream(true)
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null"))).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(5, TimeUnit.MINUTES) || process.exitValue() != 0) {
                throw new IllegalStateException("docker compose " + String.join(" ", args) + " failed: " + output);
            }
            return output;
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("Could not run docker compose in " + INFRA_DIR, ex);
        }
    }

    /** Registers a fictional member and returns its access token (registering logs in, D-36). */
    static String newMember(String label) {
        String email = label + "-" + System.nanoTime() + "@example.test";
        Response registered = post(GATEWAY + "/api/v1/auth/register", null, java.util.Map.of(
                "email", email, "password", "system-test-pass", "displayName", "System test " + label));
        if (registered.status() != 201) {
            throw new IllegalStateException("Register failed: " + registered.status());
        }
        return registered.body().get("accessToken").asString();
    }

    /** One value from booking_db, read as the PostgreSQL superuser inside its container. */
    static String sql(String query) {
        return composeExec("postgres", 1, "sh", "-c",
                "psql -q -t -A -U \"$POSTGRES_USER\" -d booking_db -c \"" + query.replace("\"", "\\\"") + "\"");
    }

    static JsonNode json(String text) {
        return JSON.readTree(text);
    }
}
