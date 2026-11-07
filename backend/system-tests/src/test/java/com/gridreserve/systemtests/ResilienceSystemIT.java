package com.gridreserve.systemtests;

import com.gridreserve.systemtests.Stack.Response;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What happens when parts of the platform fail (FS-32, FS-34, FS-37, FS-38) and configuration changes at
 * runtime (US-13), on the running Compose stack with two booking-service instances. Each test stops or
 * kills something real, checks the promise, then starts it again and waits until the gateway routes.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(2)
class ResilienceSystemIT {

    private static final ZoneId VENUE_ZONE = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void stackReady() throws InterruptedException {
        Stack.awaitReady(2);
    }

    private static long bookable(String name, String token) {
        for (JsonNode venue : Stack.get(Stack.GATEWAY + "/api/v1/venues?size=100", token).body().get("items")) {
            for (JsonNode bookable : Stack.get(Stack.GATEWAY + "/api/v1/venues/" + venue.get("id").asLong()
                    + "/bookables?size=100", token).body().get("items")) {
                if (bookable.get("name").asString().equals(name)) {
                    return bookable.get("id").asLong();
                }
            }
        }
        throw new IllegalStateException("No bookable named " + name);
    }

    /**
     * Free slots of a bookable tomorrow and the day after, oldest first, starting at least 3 hours from now,
     * so a confirmed booking can still be cancelled (the cutoff is 2 hours, CXL-02).
     */
    private static List<JsonNode> freeSlots(long bookableId, String token) {
        java.time.Instant earliest = java.time.Instant.now().plus(java.time.Duration.ofHours(3));
        List<JsonNode> free = new ArrayList<>();
        LocalDate tomorrow = LocalDate.now(VENUE_ZONE).plusDays(1);
        for (LocalDate day : List.of(tomorrow, tomorrow.plusDays(1))) {
            Stack.get(Stack.GATEWAY + "/api/v1/bookables/" + bookableId + "/availability?date=" + day, token)
                    .body().get("slots").forEach(slot -> {
                        if (slot.get("status").asString().equals("FREE")
                                && java.time.Instant.parse(slot.get("start").asString()).isAfter(earliest)) {
                            free.add(slot);
                        }
                    });
        }
        return free;
    }

    private static Response hold(String token, long bookableId, JsonNode slot) {
        return Stack.post(Stack.GATEWAY + "/api/v1/bookings", token, Map.of("bookableId", bookableId,
                "start", slot.get("start").asString(), "end", slot.get("end").asString()));
    }

    private static Response availability(String token, long bookableId) {
        return Stack.get(Stack.GATEWAY + "/api/v1/bookables/" + bookableId + "/availability?date="
                + LocalDate.now(VENUE_ZONE).plusDays(1), token);
    }

    @Test
    @Order(1)
    @DisplayName("FS-32 / RES-07: identity-service down → hold, confirm, cancel and availability work; login gets 503")
    void identityDown_FS32() throws InterruptedException {
        String member = Stack.newMember("fs32");
        long bay = bookable("Visitor Bay 2", member);
        JsonNode slot = freeSlots(bay, member).getFirst();

        Stack.compose("stop", "identity-service");
        try {
            assertThat(availability(member, bay).status()).isEqualTo(200);
            Response held = hold(member, bay, slot);
            assertThat(held.status()).isEqualTo(201);
            long id = held.body().get("id").asLong();
            assertThat(Stack.post(Stack.GATEWAY + "/api/v1/bookings/" + id + "/confirm", member, null).status()).isEqualTo(200);
            assertThat(Stack.post(Stack.GATEWAY + "/api/v1/bookings/" + id + "/cancel", member, null).status()).isEqualTo(200);

            Response login = Stack.post(Stack.GATEWAY + "/api/v1/auth/login", null,
                    Map.of("email", "admin@gridreserve.test", "password", "whatever-it-is"));
            assertThat(login.status()).isEqualTo(503);
            assertThat(login.code()).isEqualTo("SERVICE_UNAVAILABLE");
        } finally {
            Stack.compose("up", "-d", "--wait", "identity-service");
        }
    }

    @Test
    @Order(2)
    @DisplayName("FS-37: Config Server down → running services are unaffected")
    void configServerDown_FS37() {
        String member = Stack.newMember("fs37");
        long bay = bookable("Visitor Bay 3", member);
        JsonNode slot = freeSlots(bay, member).getFirst();

        Stack.compose("stop", "config-server");
        try {
            assertThat(availability(member, bay).status()).isEqualTo(200);
            Response held = hold(member, bay, slot);
            assertThat(held.status()).isEqualTo(201);
            Stack.post(Stack.GATEWAY + "/api/v1/bookings/" + held.body().get("id").asLong() + "/cancel", member, null);
        } finally {
            Stack.compose("up", "-d", "--wait", "config-server");
        }
    }

    @Test
    @Order(3)
    @DisplayName("FS-38: Eureka down → existing routes keep working from the cached registry")
    void eurekaDown_FS38() {
        String member = Stack.newMember("fs38");
        long bay = bookable("Visitor Bay 4", member);

        Stack.compose("stop", "discovery-server");
        try {
            for (int request = 0; request < 10; request++) {
                assertThat(availability(member, bay).status()).as("request %d", request).isEqualTo(200);
            }
        } finally {
            Stack.compose("up", "-d", "--wait", "discovery-server");
        }
    }

    @Test
    @Order(4)
    @DisplayName("FS-34: one booking instance killed during a burst → no 500s, GETs survive, no double bookings")
    void oneBookingInstanceKilled_FS34() throws Exception {
        String reader = Stack.newMember("fs34-reader");
        long bay = bookable("Visitor Bay 5", reader);
        List<JsonNode> slots = freeSlots(bay, reader).subList(0, 20);
        List<String> members = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            members.add(Stack.newMember("fs34-" + index));
        }
        String victim = Stack.compose("ps", "-q", "booking-service").lines().toList().getLast();

        // 20 holds (one member, one slot each) and 20 availability reads, all at once; the kill lands mid-burst.
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(41);
        List<Future<Response>> holds = new ArrayList<>();
        List<Future<Response>> reads = new ArrayList<>();
        try {
            for (int index = 0; index < slots.size(); index++) {
                int i = index;
                holds.add(pool.submit(() -> {
                    gate.await();
                    return hold(members.get(i), bay, slots.get(i));
                }));
                reads.add(pool.submit(() -> {
                    gate.await();
                    return availability(reader, bay);
                }));
            }
            Future<?> kill = pool.submit(() -> {
                gate.await();
                new ProcessBuilder("docker", "kill", victim).start().waitFor(30, TimeUnit.SECONDS);
                return null;
            });
            gate.countDown();
            kill.get(60, TimeUnit.SECONDS);

            for (Future<Response> hold : holds) {
                int status = hold.get(60, TimeUnit.SECONDS).status();
                // A hold that was in flight on the killed instance may fail; it never answers 500.
                assertThat(status).isIn(201, 502, 503, 504);
            }
            int readsOk = 0;
            for (Future<Response> read : reads) {
                int status = read.get(60, TimeUnit.SECONDS).status();
                // FS-34 allows 503 for a read caught on the dying instance; never a 500.
                assertThat(status).isIn(200, 503);
                readsOk += status == 200 ? 1 : 0;
            }
            System.out.println("FS-34: " + readsOk + " of " + reads.size() + " reads during the kill answered 200");
            // After the kill, reads recover on the surviving instance: 5 in a row answer 200 within a minute.
            assertThat(fiveReadsInARowSucceed(reader, bay)).as("reads recover after the kill").isTrue();
            // The database never holds two active bookings for one slot (BR-10).
            assertThat(Stack.sql("SELECT count(*) FROM (SELECT start_time FROM booking WHERE bookable_id = " + bay
                    + " AND status IN ('HELD','CONFIRMED') GROUP BY start_time HAVING count(*) > 1) d")).isEqualTo("0");
        } finally {
            pool.shutdownNow();
            Stack.compose("up", "-d", "--scale", "booking-service=2", "--wait");
        }
    }

    @Test
    @Order(5)
    @DisplayName("US-13 / PLAT-05: change a threshold in the config repo, run the refresh script, both instances use it")
    void refreshReachesBothInstances_US13() throws Exception {
        String configDir = System.getProperty("gridreserve.config.dir");
        Assumptions.assumeTrue(configDir != null,
                "Edits the config repo: runs only with -Dgridreserve.config.dir (CI's throwaway copy, or on purpose)");
        Path bookingConfig = Path.of(configDir, "booking-service.yml");
        String original = Files.readString(bookingConfig, StandardCharsets.UTF_8);
        String anchor = "      identity-service:\n        base-config: default\n";
        assertThat(original).contains(anchor);
        String admin = adminToken();
        Stack.awaitBookingRegistryInSync();
        try {
            Files.writeString(bookingConfig, original.replace(anchor, anchor + "        failure-rate-threshold: 65\n"),
                    StandardCharsets.UTF_8);
            assertThat(refreshScript("booking-service")).as("instances the script refreshed").isEqualTo(2);

            assertThat(threshold(1, admin)).isEqualTo(65.0);
            assertThat(threshold(2, admin)).isEqualTo(65.0);
        } finally {
            Files.writeString(bookingConfig, original, StandardCharsets.UTF_8);
            refreshScript("booking-service");
        }
        assertThat(threshold(1, admin)).isEqualTo(50.0);
        assertThat(threshold(2, admin)).isEqualTo(50.0);
    }

    private static boolean fiveReadsInARowSucceed(String token, long bookableId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
        int inARow = 0;
        while (System.nanoTime() < deadline && inARow < 5) {
            inARow = availability(token, bookableId).status() == 200 ? inARow + 1 : 0;
            if (inARow < 5) {
                Thread.sleep(500);
            }
        }
        return inARow == 5;
    }

    private static String adminToken() {
        return Stack.post(Stack.GATEWAY + "/api/v1/auth/login", null, Map.of(
                "email", System.getProperty("gridreserve.admin.email", "admin@gridreserve.test"),
                "password", System.getProperty("gridreserve.admin.password", "demo-admin-pass")))
                .body().get("accessToken").asString();
    }

    private static double threshold(int instance, String admin) {
        String health = Stack.composeExec("booking-service", instance, "curl", "-s", "-H",
                "Authorization: Bearer " + admin, "http://localhost:8082/actuator/health");
        return Stack.json(health).get("components").get("circuitBreakers").get("details")
                .get("identity-service").get("failureRateThreshold").asDouble();
    }

    /** Runs scripts/refresh-config.sh and returns how many instances it refreshed. */
    private static long refreshScript(String service) throws IOException, InterruptedException {
        ProcessBuilder script = new ProcessBuilder("../../scripts/refresh-config.sh", service)
                .redirectErrorStream(true);
        script.environment().put("GRIDRESERVE_ADMIN_EMAIL", System.getProperty("gridreserve.admin.email", "admin@gridreserve.test"));
        script.environment().put("GRIDRESERVE_ADMIN_PASSWORD", System.getProperty("gridreserve.admin.password", "demo-admin-pass"));
        Process process = script.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(2, TimeUnit.MINUTES)).isTrue();
        System.out.println("refresh-config.sh: " + output.replaceAll("changed keys: .*", "…").trim());
        assertThat(process.exitValue()).as(output).isZero();
        return output.lines().filter(line -> line.contains("→ 200")).count();
    }
}
