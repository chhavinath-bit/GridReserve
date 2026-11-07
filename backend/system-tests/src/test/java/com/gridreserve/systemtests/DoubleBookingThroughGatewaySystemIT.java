package com.gridreserve.systemtests;

import com.gridreserve.systemtests.Stack.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONC-10 (PROJECT.md §8): 50 members ask for the same slot at the same moment, through the gateway, with
 * two booking-service instances registered in Eureka and load-balanced. Exactly one gets 201, 49 get 409
 * SLOT_TAKEN, and one active row exists, 20 times in a row. Afterwards each instance's own counters show
 * it handled some of the requests, and no transaction was killed for a deadlock.
 *
 * <p>Needs the stack: {@code cd infra && docker compose up -d --build --scale booking-service=2 --wait}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DoubleBookingThroughGatewaySystemIT {

    private static final int MEMBERS = 50;
    private static final ZoneId VENUE_ZONE = ZoneId.of("Asia/Kolkata");
    private static final String BOOKABLE = "Visitor Bay 1"; // open 24 h, 60-min grid: enough slots for 20 runs

    private static final List<String> memberTokens = new ArrayList<>();
    private static String adminToken;
    private static long bookableId;
    private static final List<JsonNode> freeSlots = new ArrayList<>();
    private static long deadlocksBefore;

    @BeforeAll
    static void setUp() throws InterruptedException {
        Stack.awaitReady(2);
        adminToken = login(System.getProperty("gridreserve.admin.email", "admin@gridreserve.test"),
                System.getProperty("gridreserve.admin.password", "demo-admin-pass"));
        // Fictional members for this run only; registering also logs them in (D-36).
        long run = System.currentTimeMillis();
        for (int index = 0; index < MEMBERS; index++) {
            Response registered = Stack.post(Stack.GATEWAY + "/api/v1/auth/register", null, Map.of(
                    "email", "conc10-" + run + "-" + index + "@gridreserve.test",
                    "password", "conc10-pass-" + index,
                    "displayName", "CONC-10 member " + index));
            assertThat(registered.status()).as("register member %d", index).isEqualTo(201);
            memberTokens.add(registered.body().get("accessToken").asString());
        }
        bookableId = findBookable(BOOKABLE);
        LocalDate tomorrow = LocalDate.now(VENUE_ZONE).plusDays(1);
        for (LocalDate day : List.of(tomorrow, tomorrow.plusDays(1))) {
            Response availability = Stack.get(Stack.GATEWAY + "/api/v1/bookables/" + bookableId
                    + "/availability?date=" + day, adminToken);
            availability.body().get("slots").forEach(slot -> {
                if (slot.get("status").asString().equals("FREE")) {
                    freeSlots.add(slot);
                }
            });
        }
        assertThat(freeSlots).as("free slots for 20 runs").hasSizeGreaterThanOrEqualTo(20);
        deadlocksBefore = Long.parseLong(Stack.sql(
                "SELECT deadlocks FROM pg_stat_database WHERE datname = 'booking_db'"));
    }

    @Test
    @Order(1)
    @DisplayName("Two booking-service instances are registered in Eureka and UP")
    void twoInstancesRegistered() {
        assertThat(Stack.upInstances("BOOKING-SERVICE")).isEqualTo(2);
    }

    @RepeatedTest(20)
    @Order(2)
    @DisplayName("CONC-10: 50 parallel holds through the gateway → exactly 1 × 201, 49 × 409 SLOT_TAKEN, one active row")
    void fiftyMembersOneSlotThroughGateway_CONC10(RepetitionInfo repetition) throws Exception {
        JsonNode slot = freeSlots.get(repetition.getCurrentRepetition() - 1);
        Map<String, Object> request = Map.of("bookableId", bookableId,
                "start", slot.get("start").asString(), "end", slot.get("end").asString());

        List<Response> responses = parallel(MEMBERS,
                index -> () -> Stack.post(Stack.GATEWAY + "/api/v1/bookings", memberTokens.get(index), request));

        Map<String, Long> outcomes = responses.stream()
                .map(response -> response.status() == 201 ? "201" : response.code())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(outcomes).containsExactlyInAnyOrderEntriesOf(Map.of("201", 1L, "SLOT_TAKEN", 49L));
        assertThat(Stack.sql("SELECT count(*) FROM booking WHERE bookable_id = " + bookableId
                + " AND start_time = '" + slot.get("start").asString() + "' AND status IN ('HELD', 'CONFIRMED')"))
                .isEqualTo("1");

        // Free the slot again so the run leaves no trace and members stay under the per-category limit.
        int winner = indexOf201(responses);
        long bookingId = responses.get(winner).body().get("id").asLong();
        assertThat(Stack.post(Stack.GATEWAY + "/api/v1/bookings/" + bookingId + "/cancel",
                memberTokens.get(winner), null).status()).isEqualTo(200);
    }

    @Test
    @Order(3)
    @DisplayName("Both instances handled booking requests, and none was killed for a deadlock")
    void bothInstancesServedAndNoDeadlocks() {
        double handledByFirst = handled(1);
        double handledBySecond = handled(2);
        System.out.printf("CONC-10: instance 1 handled %.0f holds, instance 2 handled %.0f%n",
                handledByFirst, handledBySecond);
        assertThat(handledByFirst).isPositive();
        assertThat(handledBySecond).isPositive();
        assertThat(metric(1, "gridreserve.db.retries") + metric(2, "gridreserve.db.retries")).isZero();
    }

    @AfterAll
    static void reportPostgresDeadlocks() {
        // Cross-check only: PostgreSQL reports these lazily from pooled connections, so not asserted.
        long after = Long.parseLong(Stack.sql("SELECT deadlocks FROM pg_stat_database WHERE datname = 'booking_db'"));
        System.out.println("CONC-10: pg_stat_database.deadlocks for booking_db went from " + deadlocksBefore
                + " to " + after);
    }

    // created + conflicts: every hold an instance decided, won or lost.
    private static double handled(int instance) {
        return metric(instance, "gridreserve.bookings.created") + metric(instance, "gridreserve.bookings.conflicts");
    }

    /** A counter's total on one instance, read inside its container (actuator isn't routed by the gateway). */
    private static double metric(int instance, String name) {
        String body = Stack.composeExec("booking-service", instance, "curl", "-s", "-H",
                "Authorization: Bearer " + adminToken, "http://localhost:8082/actuator/metrics/" + name);
        JsonNode json = Stack.json(body);
        if (json.get("measurements") == null) {
            return 0; // the counter was never incremented on this instance, so it doesn't exist yet
        }
        return json.get("measurements").get(0).get("value").asDouble();
    }

    private static String login(String email, String password) {
        Response login = Stack.post(Stack.GATEWAY + "/api/v1/auth/login", null, Map.of("email", email, "password", password));
        assertThat(login.status()).as("admin login").isEqualTo(200);
        return login.body().get("accessToken").asString();
    }

    private static long findBookable(String name) {
        for (JsonNode venue : Stack.get(Stack.GATEWAY + "/api/v1/venues?size=100", adminToken).body().get("items")) {
            for (JsonNode bookable : Stack.get(Stack.GATEWAY + "/api/v1/venues/" + venue.get("id").asLong()
                    + "/bookables?size=100", adminToken).body().get("items")) {
                if (bookable.get("name").asString().equals(name)) {
                    return bookable.get("id").asLong();
                }
            }
        }
        throw new IllegalStateException("No bookable named " + name);
    }

    private static int indexOf201(List<Response> responses) {
        for (int index = 0; index < responses.size(); index++) {
            if (responses.get(index).status() == 201) {
                return index;
            }
        }
        throw new IllegalStateException("No 201");
    }

    /** All tasks wait at one start gate, then go at once (same idea as the in-service ConcurrencyIT). */
    private static <T> List<T> parallel(int count, java.util.function.IntFunction<Callable<T>> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                Callable<T> work = task.apply(index);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    gate.await();
                    return work.call();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            gate.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
