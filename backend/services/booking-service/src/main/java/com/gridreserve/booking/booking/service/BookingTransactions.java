package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.booking.dto.BookingResponse;
import com.gridreserve.booking.booking.dto.CreateHoldRequest;
import com.gridreserve.booking.booking.entity.Booking;
import com.gridreserve.booking.booking.entity.BookingStatus;
import com.gridreserve.booking.booking.entity.CancelledBy;
import com.gridreserve.booking.booking.mapper.BookingMapper;
import com.gridreserve.booking.booking.repository.BookingRepository;
import com.gridreserve.booking.booking.service.HoldExpiryMetrics.Layer;
import com.gridreserve.booking.catalog.dto.BookableInfo;
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
    private final HoldExpiryMetrics expiryMetrics;
    private final Clock clock;

    BookingTransactions(BookingRepository bookings, CatalogService catalog, MemberLock memberLock,
                        HoldExpiryMetrics expiryMetrics, Clock clock) {
        this.bookings = bookings;
        this.catalog = catalog;
        this.memberLock = memberLock;
        this.expiryMetrics = expiryMetrics;
        this.clock = clock;
    }

    /**
     * Creates a HELD booking (FR-BK-01). Locks in the global order (CLAUDE.md §8.3): venue and bookable
     * {@code FOR SHARE}, member {@code FOR UPDATE}, then the stale holds in the way. Rules in the BR-14
     * order; the member limit (BR-08) is counted under the member lock, so two parallel holds of one
     * member can't both see room for one more. The insert is the real decision on overlaps: the exclusion
     * constraints reject one even if two requests got this far at the same time.
     */
    @Transactional
    public BookingResponse hold(long memberId, CreateHoldRequest request) {
        // PostgreSQL keeps microseconds; the response must show the same values a later read returns.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LockedBookable bookable = catalog.lockForBooking(request.bookableId());
        memberLock.lock(memberId);
        BookingRules.checkHold(bookable, request.start(), request.end(), now);
        long active = bookings.countActive(memberId, catalog.bookableIdsInCategory(bookable.categoryId()), now,
                BookingStatus.CONFIRMED, BookingStatus.HELD);
        BookingRules.checkMemberLimit(active, bookable.rules());

        Instant occupiedUntil = request.end().plus(Duration.ofMinutes(bookable.rules().bufferMinutes()));
        List<Long> stale = bookings.lockStaleHolds(request.bookableId(), memberId, request.start(), request.end(),
                occupiedUntil, now);
        if (!stale.isEmpty()) {
            expiryMetrics.expired(Layer.HOLD, bookings.expireHolds(stale, now));
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
        expiryMetrics.expired(Layer.CONFIRM, bookings.expireHolds(List.of(bookingId), now));
        BookingStatus current = currentStatus(bookingId);
        if (current == BookingStatus.EXPIRED) {
            throw new DomainException(BookingErrorCode.HOLD_EXPIRED,
                    "Your hold has run out. Please pick the slot again.");
        }
        throw invalidState(current);
    }

    /**
     * HELD or CONFIRMED → CANCELLED (FR-BK-04, CXL-01..06), by the owner or an admin. The policy is part
     * of the conditional UPDATE: a live hold can always be cancelled (CXL-01); a confirmed booking by a
     * member only until start minus the category's cutoff (CXL-02), by an admin until it ends (CXL-03);
     * nothing after the end (CXL-04). Cancelling frees the slot at once (CXL-05). If the UPDATE refuses,
     * the current row says why. Like confirm, a run-out hold is saved as EXPIRED before the refusal.
     */
    @Transactional(noRollbackFor = DomainException.class)
    public BookingResponse cancel(long callerId, boolean admin, long bookingId, String reason) {
        Instant now = clock.instant();
        Booking booking = owned(bookingId, callerId, admin);
        CancelledBy by = booking.getMemberId() == callerId ? CancelledBy.MEMBER : CancelledBy.ADMIN;
        Duration cutoff = Duration.ofMinutes(describe(booking).cancellationCutoffMinutes());
        Instant confirmedStartAfter = admin ? Instant.EPOCH : now.plus(cutoff);
        if (bookings.cancel(bookingId, by.name(), reason, confirmedStartAfter, now) == 1) {
            return reload(bookingId);
        }

        expiryMetrics.expired(Layer.CANCEL, bookings.expireHolds(List.of(bookingId), now));
        Booking current = bookings.findById(bookingId).orElseThrow();
        boolean active = current.getStatus() == BookingStatus.HELD || current.getStatus() == BookingStatus.CONFIRMED;
        if (active && !current.getEndTime().isAfter(now)) {
            throw new DomainException(BookingErrorCode.INVALID_STATE, "This booking has already ended.");
        }
        if (current.getStatus() == BookingStatus.CONFIRMED) {
            throw new DomainException(BookingErrorCode.CUTOFF_PASSED, "Cancellations close "
                    + formatCutoff(cutoff) + " before the start. Contact the venue to change this booking.");
        }
        throw invalidState(current.getStatus());
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
        return BookingMapper.toResponse(booking, describe(booking));
    }

    private BookableInfo describe(Booking booking) {
        return catalog.describe(List.of(booking.getBookableId())).get(booking.getBookableId());
    }

    // "2 hours", "90 minutes": cutoffs are configured in minutes per category.
    private static String formatCutoff(Duration cutoff) {
        long minutes = cutoff.toMinutes();
        if (minutes % 60 == 0) {
            return minutes == 60 ? "1 hour" : minutes / 60 + " hours";
        }
        return minutes + " minutes";
    }

    private static DomainException invalidState(BookingStatus status) {
        return new DomainException(BookingErrorCode.INVALID_STATE,
                "This booking is " + status.name().toLowerCase() + " and can't be changed this way.");
    }
}
