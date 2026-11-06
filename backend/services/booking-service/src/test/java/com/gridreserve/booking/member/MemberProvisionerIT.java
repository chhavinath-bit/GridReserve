package com.gridreserve.booking.member;

import com.gridreserve.booking.member.service.MemberProvisioner;
import com.gridreserve.booking.support.ServiceIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** The local member row is created once, however often and however concurrently it is asked for. */
@ServiceIntegrationTest
class MemberProvisionerIT {

    // Far above the seeded and other tests' IDs, so the rows are easy to clean up.
    private static final long MEMBER_ID = 900_001L;
    private static final long RACING_MEMBER_ID = 900_002L;

    @Autowired
    MemberProvisioner provisioner;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM member WHERE id IN (?, ?)", MEMBER_ID, RACING_MEMBER_ID);
    }

    @Test
    @DisplayName("ARCH 5.3: ensure() creates the row once; a second call changes nothing")
    void ensureIsIdempotent() {
        assertThat(provisioner.ensure(MEMBER_ID)).isTrue();
        Timestamp createdAt = jdbc.queryForObject("SELECT created_at FROM member WHERE id = ?",
                Timestamp.class, MEMBER_ID);

        assertThat(provisioner.ensure(MEMBER_ID)).isFalse();

        assertThat(rows(MEMBER_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT created_at FROM member WHERE id = ?", Timestamp.class, MEMBER_ID))
                .isEqualTo(createdAt);
    }

    @Test
    @DisplayName("ARCH 5.3: 10 parallel first requests of one user → exactly one row, one creator, no error")
    void parallelEnsureCreatesOneRow() throws Exception {
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return provisioner.ensure(RACING_MEMBER_ID);
                }));
            }
            start.countDown();

            int created = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
            assertThat(rows(RACING_MEMBER_ID)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private int rows(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM member WHERE id = ?", Integer.class, id);
    }
}
