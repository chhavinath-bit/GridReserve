package com.gridreserve.booking.booking;

import com.gridreserve.booking.booking.job.HoldExpiryJob;
import com.gridreserve.booking.booking.service.HoldExpiryMetrics;
import com.gridreserve.booking.booking.service.HoldExpiryMetrics.Layer;
import com.gridreserve.booking.member.service.MemberProvisioner;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import com.gridreserve.booking.support.TestBookings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-EXP-01: the background sweep, against real PostgreSQL. Two sweeps start at the same moment over the
 * same run-out holds (as two booking-service instances would); every hold must be expired exactly once,
 * and nothing else touched. Repeated 20 times (CLAUDE.md §8.3).
 */
@ServiceIntegrationTest
class HoldExpiryJobIT {

    private static final long MEMBER = 940_001L;
    private static final int RUN_OUT = 40;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MemberProvisioner members;

    @Autowired
    HoldExpiryJob job;

    @Autowired
    HoldExpiryMetrics metrics;

    long court;
    Instant base;

    @BeforeEach
    void setUp() {
        members.ensure(MEMBER);
        court = jdbc.queryForObject("SELECT id FROM bookable WHERE name = 'Court A'", Long.class);
        base = Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS);
    }

    @AfterEach
    void cleanUp() {
        TestBookings.delete(jdbc, "member_id = ?", MEMBER);
    }

    /** One booking per hour from {@code base}, so none overlap (BR-10, BR-11 stay quiet). */
    private long insert(int hour, String status, Instant expiresAt) {
        Instant start = base.plus(Duration.ofHours(hour));
        return jdbc.queryForObject("""
                INSERT INTO booking (member_id, bookable_id, start_time, end_time, occupied_until, status,
                                     expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, MEMBER, court, Timestamp.from(start), Timestamp.from(start.plusSeconds(3600)),
                Timestamp.from(start.plusSeconds(3600)), status, expiresAt == null ? null : Timestamp.from(expiresAt));
    }

    private int countStatus(String status) {
        return jdbc.queryForObject("SELECT count(*) FROM booking WHERE member_id = ? AND status = ?", Integer.class,
                MEMBER, status);
    }

    @RepeatedTest(20)
    @DisplayName("FR-EXP-01: two sweeps in parallel expire every run-out hold exactly once; live holds and confirmed bookings untouched")
    void parallelSweepsExpireEachHoldOnce_FREXP01() throws Exception {
        Instant past = Instant.now().minus(Duration.ofMinutes(1));
        for (int hour = 0; hour < RUN_OUT; hour++) {
            insert(hour, "HELD", past);
        }
        long live = insert(RUN_OUT, "HELD", Instant.now().plus(Duration.ofMinutes(5)));
        long confirmed = insert(RUN_OUT + 1, "CONFIRMED", null);
        double jobCountBefore = metrics.counter(Layer.JOB).count();

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> sweeps = new ArrayList<>();
        try {
            for (int instance = 0; instance < 2; instance++) {
                sweeps.add(pool.submit(() -> {
                    startGate.await();
                    return job.run();
                }));
            }
            startGate.countDown();
            int expiredBySweeps = 0;
            for (Future<Integer> sweep : sweeps) {
                expiredBySweeps += sweep.get();
            }

            assertThat(expiredBySweeps).isEqualTo(RUN_OUT);
        } finally {
            pool.shutdownNow();
        }
        assertThat(countStatus("EXPIRED")).isEqualTo(RUN_OUT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking WHERE member_id = ? AND status = 'EXPIRED' "
                + "AND expires_at IS NOT NULL", Integer.class, MEMBER)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, live)).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, confirmed))
                .isEqualTo("CONFIRMED");
        assertThat(metrics.counter(Layer.JOB).count() - jobCountBefore).isEqualTo(RUN_OUT);

        // A sweep with nothing left to do does nothing.
        assertThat(job.run()).isZero();
    }
}
