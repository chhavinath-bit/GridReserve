package com.gridreserve.booking.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/**
 * Runs N tasks at the same moment: every thread is started first and waits at a start gate
 * ({@link CountDownLatch}); then the gate opens and all go at once. Without the gate the first threads
 * would finish before the last ones even start, and nothing would really race (CLAUDE.md §10).
 */
public final class Parallel {

    private Parallel() {
    }

    public static <T> List<T> run(int count, IntFunction<Callable<T>> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                Callable<T> work = task.apply(index);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    startGate.await();
                    return work.call();
                }));
            }
            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Not all threads became ready");
            }
            startGate.countDown();
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
