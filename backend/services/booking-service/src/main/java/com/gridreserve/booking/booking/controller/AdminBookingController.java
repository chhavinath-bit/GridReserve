package com.gridreserve.booking.booking.controller;

import com.gridreserve.booking.audit.dto.AuditEntryResponse;
import com.gridreserve.booking.booking.dto.AdminBookingPage;
import com.gridreserve.booking.booking.entity.BookingStatus;
import com.gridreserve.booking.booking.service.AdminBookingService;
import com.gridreserve.booking.common.web.PageParams;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Oversight for admins (FR-ADM-01, FR-AUD-03, US-10); {@code /api/v1/admin/**} is ADMIN only (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/bookings")
public class AdminBookingController {

    private final AdminBookingService bookings;

    public AdminBookingController(AdminBookingService bookings) {
        this.bookings = bookings;
    }

    /** Newest start first. {@code from}/{@code to} filter on the start time, as UTC instants. */
    @GetMapping
    public AdminBookingPage list(@RequestParam(required = false) Long venueId,
                                 @RequestParam(required = false) Long bookableId,
                                 @RequestParam(required = false) BookingStatus status,
                                 @RequestParam(required = false) Instant from,
                                 @RequestParam(required = false) Instant to,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = PageParams.DEFAULT_SIZE) int size) {
        return bookings.list(venueId, bookableId, status, from, to,
                PageParams.of(page, size, Sort.by(Sort.Order.desc("startTime"), Sort.Order.desc("id"))));
    }

    /** A booking's history, oldest first (FR-AUD-03). Unknown booking → 404. */
    @GetMapping("/{id}/audit")
    public List<AuditEntryResponse> audit(@PathVariable long id) {
        return bookings.history(id);
    }
}
