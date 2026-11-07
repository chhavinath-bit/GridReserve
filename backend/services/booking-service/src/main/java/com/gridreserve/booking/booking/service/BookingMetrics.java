package com.gridreserve.booking.booking.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Booking outcomes per instance (ARCHITECTURE §17): {@code gridreserve.bookings.created{category,type}}
 * and {@code gridreserve.bookings.conflicts{code}} for 409s decided by the database. With several
 * booking-service instances, each counts only what it handled, which is how CONC-10 proves that both
 * instances took part. Tags are codes (STUDY, ROOM, SLOT_TAKEN), never member data.
 */
@Component
public class BookingMetrics {

    private final MeterRegistry registry;

    public BookingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void created(String categoryCode, String typeCode) {
        Counter.builder("gridreserve.bookings.created")
                .description("Holds created")
                .tag("category", categoryCode)
                .tag("type", typeCode)
                .register(registry)
                .increment();
    }

    /**
     * {@code gridreserve.db.retries{sqlstate}}: a transaction PostgreSQL killed to break a deadlock (40P01)
     * or a serialization conflict (40001), counted at the moment it fails, before the retry runs it again.
     * The lock order should keep this at 0; the concurrency tests assert it.
     */
    public void lockFailure(String sqlState) {
        Counter.builder("gridreserve.db.retries")
                .description("Transactions rolled back by PostgreSQL for a deadlock or serialization conflict")
                .tag("sqlstate", sqlState)
                .register(registry)
                .increment();
    }

    public double lockFailures() {
        return registry.find("gridreserve.db.retries").counters().stream().mapToDouble(Counter::count).sum();
    }

    /** "40P01=3.0" per SQLSTATE, for test failure messages. */
    public String lockFailuresBySqlState() {
        return registry.find("gridreserve.db.retries").counters().stream()
                .map(counter -> counter.getId().getTag("sqlstate") + "=" + counter.count())
                .sorted()
                .toList()
                .toString();
    }

    public void conflict(String errorCode) {
        Counter.builder("gridreserve.bookings.conflicts")
                .description("Holds refused by an exclusion constraint (409)")
                .tag("code", errorCode)
                .register(registry)
                .increment();
    }
}
