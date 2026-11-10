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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The booking module's API for the controller. Writes run in {@link BookingTransactions}, each wrapped in the
 * deadlock retry of {@link RetriedTransactions} (D-11). Hold creation may carry an {@code Idempotency-Key}
 * (IDEM-01..07): the key is claimed first in its own short transaction, the hold's transaction stores the answer
 * with the booking, and a failure either stores the refusal or gives the key back (see {@link IdempotencyService}).
 */
@Service
public class BookingService {

    private final RetriedTransactions transactions;
    private final IdempotencyService idempotency;
    private final MemberProvisioner members;
    private final BookingRepository bookings;
    private final CatalogService catalog;

    public BookingService(RetriedTransactions transactions, IdempotencyService idempotency, MemberProvisioner members,
                          BookingRepository bookings, CatalogService catalog) {
        this.transactions = transactions;
        this.idempotency = idempotency;
        this.members = members;
        this.bookings = bookings;
        this.catalog = catalog;
    }

    /**
     * BR-01 and the key's format need no data, so they are checked first (and a request failing them is never
     * stored). The member row is created in its own short transaction before anything else, so the key's row
     * and the hold transaction only ever refer to an existing member (D-38).
     */
    public HoldResult hold(long memberId, CreateHoldRequest request, String idempotencyKey) {
        BookingRules.checkTimes(request.start(), request.end());
        if (idempotencyKey != null) {
            IdempotencyService.checkFormat(idempotencyKey);
        }
        members.ensure(memberId);
        if (idempotencyKey == null) {
            BookingResponse hold = transactions.hold(memberId, request, null);
            return new HoldResult(201, hold, hold.id(), false);
        }

        String hash = IdempotencyService.hash(request);
        Optional<IdempotencyService.StoredResponse> stored = idempotency.claim(memberId, idempotencyKey, hash);
        if (stored.isPresent()) {
            return replay(stored.get());
        }
        try {
            BookingResponse hold = transactions.hold(memberId, request, new IdempotencyKey(memberId, idempotencyKey, hash));
            return new HoldResult(201, hold, hold.id(), false);
        } catch (DomainException refusal) {
            idempotency.storeRefusal(memberId, idempotencyKey, hash, refusal);
            throw refusal;
        } catch (RuntimeException failure) {
            idempotency.release(memberId, idempotencyKey);
            throw failure;
        }
    }

    public BookingResponse confirm(long memberId, long bookingId) {
        return transactions.confirm(memberId, bookingId);
    }

    public BookingResponse cancel(long callerId, boolean admin, long bookingId, String reason) {
        return transactions.cancel(callerId, admin, bookingId, reason);
    }

    // The first request's answer, again: the same booking, or the same refusal (IDEM-03).
    private HoldResult replay(IdempotencyService.StoredResponse stored) {
        if (stored.bookingId() == null) {
            throw idempotency.refusalOf(stored);
        }
        return new HoldResult(stored.status(), idempotency.bodyOf(stored), stored.bookingId(), true);
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
