package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.audit.service.Actor;
import com.gridreserve.booking.audit.service.AuditService;
import com.gridreserve.booking.booking.entity.BookingStatus;
import com.gridreserve.booking.booking.repository.BookingRepository;
import com.gridreserve.booking.booking.service.HoldExpiryMetrics.Layer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The background layer of hold expiry (FR-EXP-01). Each hold is expired in its own short transaction:
 * lock it {@code FOR UPDATE SKIP LOCKED}, then a conditional UPDATE. So a second instance running the
 * same sweep skips rows the first one is working on instead of waiting, a row can't be expired twice,
 * and stopping half-way leaves nothing half-done. (Phase 3 adds waitlist promotion and M1.12 the SYSTEM
 * audit row, FR-EXP-03, inside this same per-hold transaction.)
 */
@Service
public class HoldExpiryService {

    private final BookingRepository bookings;
    private final HoldExpiryMetrics metrics;
    private final AuditService audit;
    private final Clock clock;

    public HoldExpiryService(BookingRepository bookings, HoldExpiryMetrics metrics, AuditService audit, Clock clock) {
        this.bookings = bookings;
        this.metrics = metrics;
        this.audit = audit;
        this.clock = clock;
    }

    /** Up to {@code limit} run-out holds, oldest first. Read without locks: rows are locked one by one. */
    @Transactional(readOnly = true)
    public List<Long> runOutHolds(int limit) {
        return bookings.findRunOutHoldIds(clock.instant(), limit);
    }

    /** True when this call expired the hold; false if it was taken, confirmed or cancelled meanwhile. */
    @Transactional
    public boolean expire(long bookingId) {
        Instant now = clock.instant();
        if (bookings.lockRunOutHold(bookingId, now).isEmpty()) {
            return false;
        }
        boolean expired = bookings.expireHolds(List.of(bookingId), now) == 1;
        metrics.expired(Layer.JOB, expired ? 1 : 0);
        if (expired) {
            audit.record(bookingId, BookingStatus.HELD.name(), BookingStatus.EXPIRED.name(), Actor.SYSTEM, null, now);
        }
        return expired;
    }
}
