package com.gridreserve.booking.booking.controller;

import com.gridreserve.booking.booking.dto.BookingResponse;
import com.gridreserve.booking.booking.dto.CancelRequest;
import com.gridreserve.booking.booking.dto.CreateHoldRequest;
import com.gridreserve.booking.booking.entity.BookingStatus;
import com.gridreserve.booking.booking.service.BookingService;
import com.gridreserve.booking.booking.service.HoldResult;
import com.gridreserve.booking.booking.service.IdempotencyService;
import com.gridreserve.booking.common.dto.PageResponse;
import com.gridreserve.booking.common.web.PageParams;
import com.gridreserve.booking.common.web.Viewer;
import com.gridreserve.common.web.error.ErrorResponse.FieldError;
import com.gridreserve.common.web.error.FieldValidationException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Booking lifecycle (FR-BK-01..06). The owner is always the caller's token {@code sub}; no endpoint
 * takes a member ID (FR-ROLE-05), so even an admin can only book for themself.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    /**
     * An optional {@code Idempotency-Key} makes retries safe (IDEM-01): the same key with the same request gets
     * the first answer again, marked {@code Idempotent-Replayed: true}, and no second booking.
     */
    @PostMapping
    public ResponseEntity<Object> hold(@Valid @RequestBody CreateHoldRequest request,
                                       @RequestHeader(name = IdempotencyService.HEADER, required = false) String key,
                                       Authentication caller) {
        HoldResult result = bookings.hold(Viewer.memberId(caller), request, key);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .location(URI.create("/api/v1/bookings/" + result.bookingId()));
        if (result.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }
        return response.body(result.body());
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

    /**
     * The caller's bookings (FR-BK-05). {@code status} may repeat ({@code ?status=HELD&status=CONFIRMED}); {@code
     * from}/{@code to} filter on the start time, as UTC instants; {@code order} is {@code desc} (newest start
     * first, the default) or {@code asc} (soonest first, for "upcoming").
     */
    @GetMapping("/me")
    public PageResponse<BookingResponse> mine(@RequestParam(required = false) List<BookingStatus> status,
                                              @RequestParam(required = false) Instant from,
                                              @RequestParam(required = false) Instant to,
                                              @RequestParam(defaultValue = "desc") String order,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = PageParams.DEFAULT_SIZE) int size,
                                              Authentication caller) {
        Sort.Direction direction = switch (order) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new FieldValidationException(List.of(new FieldError("order", "Must be asc or desc.")));
        };
        return bookings.mine(Viewer.memberId(caller), status == null ? List.of() : status, from, to,
                PageParams.of(page, size, Sort.by(new Sort.Order(direction, "startTime"), new Sort.Order(direction, "id"))));
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable long id, Authentication caller) {
        return bookings.get(Viewer.memberId(caller), Viewer.isAdmin(caller), id);
    }
}
