package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.booking.dto.BookingResponse;
import com.gridreserve.booking.booking.dto.CreateHoldRequest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * The deadlock retry (D-11) around each write transaction in {@link BookingTransactions}: PostgreSQL killed
 * the transaction to break a deadlock ({@code 40P01}) or a serialization conflict ({@code 40001}), which Spring
 * reports as a {@link PessimisticLockingFailureException}; the killed transaction changed nothing, so it runs
 * again. Business refusals are never retried. A bean of its own (M1.12) so that only the transaction is
 * retried, never the idempotency claim before it: a retried claim would find its own key in progress.
 */
@Service
class RetriedTransactions {

    private final BookingTransactions transactions;
    private final BookingMetrics metrics;

    RetriedTransactions(BookingTransactions transactions, BookingMetrics metrics) {
        this.transactions = transactions;
        this.metrics = metrics;
    }

    @Retryable(includes = PessimisticLockingFailureException.class, maxRetries = 2, delay = 50, jitter = 25)
    public BookingResponse hold(long memberId, CreateHoldRequest request, IdempotencyKey key) {
        return counted(() -> transactions.hold(memberId, request, key));
    }

    @Retryable(includes = PessimisticLockingFailureException.class, maxRetries = 2, delay = 50, jitter = 25)
    public BookingResponse confirm(long memberId, long bookingId) {
        return counted(() -> transactions.confirm(memberId, bookingId));
    }

    @Retryable(includes = PessimisticLockingFailureException.class, maxRetries = 2, delay = 50, jitter = 25)
    public BookingResponse cancel(long callerId, boolean admin, long bookingId, String reason) {
        return counted(() -> transactions.cancel(callerId, admin, bookingId, reason));
    }

    /**
     * Runs one attempt and counts it if PostgreSQL killed it for a deadlock. The retry proxy sits outside
     * this method, so every failed attempt passes through here once.
     */
    private <T> T counted(Supplier<T> attempt) {
        try {
            return attempt.get();
        } catch (PessimisticLockingFailureException ex) {
            metrics.lockFailure(NestedExceptionUtils.getMostSpecificCause(ex) instanceof SQLException sql
                    && sql.getSQLState() != null ? sql.getSQLState() : "unknown");
            throw ex;
        }
    }
}
