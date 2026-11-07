package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.booking.dto.BookingResponse;
import com.gridreserve.booking.booking.dto.CreateHoldRequest;
import com.gridreserve.booking.booking.entity.Booking;
import com.gridreserve.booking.booking.entity.BookingStatus;
import com.gridreserve.booking.booking.entity.CancelledBy;
import com.gridreserve.booking.booking.mapper.BookingMapper;
import com.gridreserve.booking.booking.repository.BookingRepository;
import com.gridreserve.booking.catalog.dto.LockedBookable;
import com.gridreserve.booking.catalog.service.CatalogService;
import com.gridreserve.booking.common.exception.BookingErrorCode;
import com.gridreserve.booking.common.exception.ConstraintViolations;
import com.gridreserve.booking.member.service.MemberLock;
import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.DomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Each public method is one booking decision in one database transaction (CLAUDE.md §6.2). Called only
 * through {@link BookingService}, which retries a whole transaction after a deadlock; that is why the
 * transactions live in their own class (each retry must start a new transaction).
 */
@Service
class BookingTransactions {

    // A-04: holds last 5 minutes in Phase 1.
    static final Duration HOLD_DURATION = Duration.ofMinutes(5);

    private final BookingRepository bookings;
    private final CatalogService catalog;
    private final MemberLock memberLock;
    private final Clock clock;

    BookingTransactions(BookingRepository bookings, CatalogService catalog, MemberLock memberLock, Clock clock) {
        this.bookings = bookings;
        this.catalog = catalog;
        this.memberLock = memberLock;
        this.clock = clock;
    }

    /**
     * Creates a HELD booking (FR-BK-01). Locks in the global order (CLAUDE.md §8.3): venue and bookable
     * {@code FOR SHARE}, member {@code FOR UPDATE}, then the stale holds in the way. The insert is the
     * real decision: the exclusion constraints reject an overlap even if two requests got this far at
     * the same time.
     */
    @Transactional
    public BookingResponse hold(long memberId, CreateHoldRequest request) {
        // PostgreSQL keeps microseconds; the response must show the same values a later read returns.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LockedBookable bookable = catalog.lockForBooking(request.bookableId());
        memberLock.lock(memberId);
        BookingRules.checkHold(bookable, request.start(), now);

        Instant occupiedUntil = request.end().plus(Duration.ofMinutes(bookable.rules().bufferMinutes()));
        List<Long> stale = bookings.lockStaleHolds(request.bookableId(), memberId, request.start(), request.end(),
                occupiedUntil, now);
        if (!stale.isEmpty()) {
            bookings.expireHolds(stale, now);
        }

        Booking hold = Booking.hold(memberId, request.bookableId(), request.start(), request.end(), occupiedUntil,
                now.plus(HOLD_DURATION), now);
        try {
            // Flush now, so a constraint violation surfaces here and not at commit, outside this try.
            bookings.saveAndFlush(hold);
        } catch (DataIntegrityViolationException ex) {
            throw ConstraintViolations.translate(ex);
        }
        return BookingMapper.toResponse(hold, bookable.info());
    }

    /**
     * HELD → CONFIRMED (FR-BK-03). If the hold has run out, the booking is marked EXPIRED and the caller
     * gets {@code 409 HOLD_EXPIRED}. That EXPIRED must be saved although an exception is thrown, so this
     * transaction does not roll back on a {@link DomainException} (the default would undo it).
     */
    @Transactional(noRollbackFor = DomainException.class)
    public BookingResponse confirm(long memberId, long bookingId) {
        Instant now = clock.instant();
        owned(bookingId, memberId, false);
        if (bookings.confirm(bookingId, now) == 1) {
            return reload(bookingId);
        }
        // Not confirmed: either the hold ran out (record that now) or the booking is past HELD.
        bookings.expireHolds(List.of(bookingId), now);
        BookingStatus current = currentStatus(bookingId);
        if (current == BookingStatus.EXPIRED) {
            throw new DomainException(BookingErrorCode.HOLD_EXPIRED,
                    "Your hold has run out. Please pick the slot again.");
        }
        throw invalidState(current);
    }

    /**
     * HELD or CONFIRMED → CANCELLED (FR-BK-04), by the owner or an admin. The cutoff rules (CXL-02..04)
     * come in M1.7. Cancelling frees the slot at once: the row stops being active in this transaction.
     * Like confirm, a run-out hold is saved as EXPIRED before the refusal, so no rollback on refusal.
     */
    @Transactional(noRollbackFor = DomainException.class)
    public BookingResponse cancel(long callerId, boolean admin, long bookingId, String reason) {
        Instant now = clock.instant();
        Booking booking = owned(bookingId, callerId, admin);
        CancelledBy by = booking.getMemberId() == callerId ? CancelledBy.MEMBER : CancelledBy.ADMIN;
        if (bookings.cancel(bookingId, by.name(), reason, now) == 1) {
            return reload(bookingId);
        }
        // A hold that ran out is recorded as EXPIRED, so the answer names its real state.
        bookings.expireHolds(List.of(bookingId), now);
        throw invalidState(currentStatus(bookingId));
    }

    /**
     * The booking if the caller may act on it. Someone else's booking is "not found", not "forbidden",
     * so a member cannot even learn that it exists (FR-ROLE-04).
     */
    private Booking owned(long bookingId, long callerId, boolean admin) {
        return bookings.findById(bookingId)
                .filter(booking -> admin || booking.getMemberId() == callerId)
                .orElseThrow(() -> new DomainException(CommonErrorCode.NOT_FOUND, "No such booking."));
    }

    // Read again after a conditional UPDATE: the row may have changed since it was first loaded.
    private BookingStatus currentStatus(long bookingId) {
        return bookings.findById(bookingId).orElseThrow().getStatus();
    }

    private BookingResponse reload(long bookingId) {
        Booking booking = bookings.findById(bookingId).orElseThrow();
        return BookingMapper.toResponse(booking, catalog.describe(List.of(booking.getBookableId()))
                .get(booking.getBookableId()));
    }

    private static DomainException invalidState(BookingStatus status) {
        return new DomainException(BookingErrorCode.INVALID_STATE,
                "This booking is " + status.name().toLowerCase() + " and can't be changed this way.");
    }
}
