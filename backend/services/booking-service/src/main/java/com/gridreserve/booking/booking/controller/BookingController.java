package com.gridreserve.booking.booking.controller;

import com.gridreserve.booking.booking.dto.BookingResponse;
import com.gridreserve.booking.booking.dto.CancelRequest;
import com.gridreserve.booking.booking.dto.CreateHoldRequest;
import com.gridreserve.booking.booking.entity.BookingStatus;
import com.gridreserve.booking.booking.service.BookingService;
import com.gridreserve.booking.common.dto.PageResponse;
import com.gridreserve.booking.common.web.PageParams;
import com.gridreserve.booking.common.web.Viewer;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

/**
 * Booking lifecycle (FR-BK-01..06). The owner is always the caller's token {@code sub}; no endpoint
 * takes a member ID (FR-ROLE-05), so even an admin can only book for themself.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> hold(@Valid @RequestBody CreateHoldRequest request, Authentication caller) {
        BookingResponse hold = bookings.hold(Viewer.memberId(caller), request);
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + hold.id())).body(hold);
    }

    @PostMapping("/{id}/confirm")
    public BookingResponse confirm(@PathVariable long id, Authentication caller) {
        return bookings.confirm(Viewer.memberId(caller), id);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable long id, @Valid @RequestBody(required = false) CancelRequest request,
                                  Authentication caller) {
        String reason = request == null ? null : request.reason();
        return bookings.cancel(Viewer.memberId(caller), Viewer.isAdmin(caller), id, reason);
    }

    /** Newest start first (FR-BK-05). {@code from}/{@code to} filter on the start time, as UTC instants. */
    @GetMapping("/me")
    public PageResponse<BookingResponse> mine(@RequestParam(required = false) BookingStatus status,
                                              @RequestParam(required = false) Instant from,
                                              @RequestParam(required = false) Instant to,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = PageParams.DEFAULT_SIZE) int size,
                                              Authentication caller) {
        return bookings.mine(Viewer.memberId(caller), status, from, to,
                PageParams.of(page, size, Sort.by(Sort.Order.desc("startTime"), Sort.Order.desc("id"))));
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable long id, Authentication caller) {
        return bookings.get(Viewer.memberId(caller), Viewer.isAdmin(caller), id);
    }
}
