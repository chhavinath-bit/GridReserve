package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.booking.dto.BookingResponse;
import com.gridreserve.booking.booking.dto.CreateHoldRequest;
import com.gridreserve.booking.booking.entity.Booking;
import com.gridreserve.booking.booking.entity.BookingStatus;
import com.gridreserve.booking.booking.mapper.BookingMapper;
import com.gridreserve.booking.booking.repository.BookingRepository;
import com.gridreserve.booking.catalog.dto.BookableInfo;
import com.gridreserve.booking.catalog.service.CatalogService;
import com.gridreserve.booking.common.dto.PageResponse;
import com.gridreserve.booking.member.service.MemberProvisioner;
import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.DomainException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The booking module's API for the controller. Writes run in {@link BookingTransactions}; this class
 * wraps them in a retry for the one failure where trying again is right (D-11): PostgreSQL killed the
 * transaction to break a deadlock ({@code 40P01}) or a serialization conflict ({@code 40001}). Spring
 * reports both as a {@link PessimisticLockingFailureException}. The killed transaction changed nothing,
 * so running it again is safe. Business refusals ({@code SLOT_TAKEN}, {@code HOLD_EXPIRED}, ...) are
 * {@link DomainException}s and are never retried: the answer would be the same.
 */
@Service
public class BookingService {

    private final BookingTransactions transactions;
    private final MemberProvisioner members;
    private final BookingRepository bookings;
    private final CatalogService catalog;
    private final BookingMetrics metrics;

    public BookingService(BookingTransactions transactions, MemberProvisioner members, BookingRepository bookings,
                          CatalogService catalog, BookingMetrics metrics) {
        this.transactions = transactions;
        this.members = members;
        this.bookings = bookings;
        this.catalog = catalog;
        this.metrics = metrics;
    }

    /**
     * BR-01 needs no data, so it is checked first. The member row is created in its own short
     * transaction before the hold's, so the hold transaction only ever locks an existing row (D-38).
     */
    @Retryable(includes = PessimisticLockingFailureException.class, maxRetries = 2, delay = 50, jitter = 25)
    public BookingResponse hold(long memberId, CreateHoldRequest request) {
        BookingRules.checkTimes(request.start(), request.end());
        members.ensure(memberId);
        return counted(() -> transactions.hold(memberId, request));
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

    /** One booking: the owner's, or any for an admin; otherwise 404 (FR-BK-06, FR-ROLE-04). */
    @Transactional(readOnly = true)
    public BookingResponse get(long callerId, boolean admin, long bookingId) {
        Booking booking = bookings.findById(bookingId)
                .filter(found -> admin || found.getMemberId() == callerId)
                .orElseThrow(() -> new DomainException(CommonErrorCode.NOT_FOUND, "No such booking."));
        return BookingMapper.toResponse(booking, catalog.describe(Set.of(booking.getBookableId()))
                .get(booking.getBookableId()));
    }

    /** The caller's bookings (FR-BK-05); names for the whole page come from one catalog query. */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> mine(long memberId, BookingStatus status, Instant from, Instant to,
                                              Pageable pageable) {
        Page<Booking> page = bookings.findMine(memberId, status, from, to, pageable);
        Map<Long, BookableInfo> names = catalog.describe(page.getContent().stream()
                .map(Booking::getBookableId).collect(Collectors.toSet()));
        return PageResponse.of(page, booking -> BookingMapper.toResponse(booking, names.get(booking.getBookableId())));
    }
}
