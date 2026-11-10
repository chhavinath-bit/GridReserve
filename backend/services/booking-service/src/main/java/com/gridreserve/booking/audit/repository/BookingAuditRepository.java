package com.gridreserve.booking.audit.repository;

import com.gridreserve.booking.audit.entity.BookingAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingAuditRepository extends JpaRepository<BookingAudit, Long> {

    /** A booking's history in time order; id breaks ties between changes at the same instant. */
    List<BookingAudit> findByBookingIdOrderByOccurredAtAscIdAsc(long bookingId);
}
