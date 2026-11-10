package com.gridreserve.booking.audit.service;

import com.gridreserve.booking.audit.dto.AuditEntryResponse;
import com.gridreserve.booking.audit.entity.BookingAudit;
import com.gridreserve.booking.audit.repository.BookingAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The booking audit log (FR-AUD-01..03). {@link #record} must run inside the transaction that changes the
 * booking ({@code MANDATORY}: without one it fails instead of opening its own), so a change and its audit row
 * commit or roll back together (FR-AUD-02). Rows are only added (FR-AUD-03).
 */
@Service
public class AuditService {

    private final BookingAuditRepository audits;

    public AuditService(BookingAuditRepository audits) {
        this.audits = audits;
    }

    /** {@code from} is null when the booking is created. Statuses as their names, e.g. "HELD". */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(long bookingId, String from, String to, Actor actor, String reason, Instant at) {
        audits.save(new BookingAudit(bookingId, from, to, actor.type(), actor.userId(), reason, at));
    }

    /** A booking's history, oldest first. */
    @Transactional(readOnly = true)
    public List<AuditEntryResponse> history(long bookingId) {
        return audits.findByBookingIdOrderByOccurredAtAscIdAsc(bookingId).stream()
                .map(row -> new AuditEntryResponse(row.getFromStatus(), row.getToStatus(), row.getActorType(),
                        row.getActorUserId(), row.getReason(), row.getOccurredAt()))
                .toList();
    }
}
