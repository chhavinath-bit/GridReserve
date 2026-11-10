package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.booking.dto.AdminBookingPage;
import com.gridreserve.booking.booking.dto.AdminBookingResponse;
import com.gridreserve.booking.booking.entity.Booking;
import com.gridreserve.booking.booking.entity.BookingStatus;
import com.gridreserve.booking.booking.mapper.BookingMapper;
import com.gridreserve.booking.booking.repository.BookingRepository;
import com.gridreserve.booking.catalog.dto.BookableInfo;
import com.gridreserve.booking.catalog.service.AdminCatalogService;
import com.gridreserve.booking.catalog.service.CatalogService;
import com.gridreserve.booking.client.IdentityGateway;
import com.gridreserve.booking.client.dto.IdentityUser;
import com.gridreserve.booking.client.dto.MemberDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * All bookings for admins (FR-ADM-01, US-10). Deliberately not {@code @Transactional}: the page is read
 * (its own short transaction), and only then is identity-service asked for the members' names, once per
 * page; no remote call ever runs inside a database transaction (CLAUDE.md §6.2). If identity can't answer,
 * {@link IdentityGateway} gives the fallback and the list still loads with member IDs only (FS-33).
 */
@Service
public class AdminBookingService {

    // An IN list can't be empty; with anyBookable = true the query ignores it.
    private static final List<Long> NO_FILTER = List.of(-1L);

    private final BookingRepository bookings;
    private final CatalogService catalog;
    private final AdminCatalogService adminCatalog;
    private final IdentityGateway identity;

    public AdminBookingService(BookingRepository bookings, CatalogService catalog, AdminCatalogService adminCatalog,
                               IdentityGateway identity) {
        this.bookings = bookings;
        this.catalog = catalog;
        this.adminCatalog = adminCatalog;
        this.identity = identity;
    }

    public AdminBookingPage list(Long venueId, Long bookableId, BookingStatus status, Instant from, Instant to,
                                 Pageable pageable) {
        Page<Booking> page = find(bookableFilter(venueId, bookableId), status, from, to, pageable);

        Map<Long, BookableInfo> names = catalog.describe(page.getContent().stream()
                .map(Booking::getBookableId).collect(Collectors.toSet()));
        MemberDirectory members = identity.members(page.getContent().stream()
                .map(Booking::getMemberId).collect(Collectors.toSet()));

        List<AdminBookingResponse> items = page.getContent().stream().map(booking -> {
            Optional<IdentityUser> member = members.find(booking.getMemberId());
            return new AdminBookingResponse(BookingMapper.toResponse(booking, names.get(booking.getBookableId())),
                    booking.getMemberId(), member.map(IdentityUser::email).orElse(null),
                    member.map(IdentityUser::displayName).orElse(null));
        }).toList();
        return new AdminBookingPage(items, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), members.available());
    }

    /** Empty = no filter; otherwise the bookings must be on one of these bookables (may be none at all). */
    private Optional<Collection<Long>> bookableFilter(Long venueId, Long bookableId) {
        if (venueId == null && bookableId == null) {
            return Optional.empty();
        }
        if (venueId == null) {
            return Optional.of(Set.of(bookableId));
        }
        List<Long> atVenue = adminCatalog.bookableIdsAt(venueId);
        return Optional.of(bookableId == null ? atVenue : atVenue.stream().filter(bookableId::equals).toList());
    }

    private Page<Booking> find(Optional<Collection<Long>> bookableIds, BookingStatus status, Instant from,
                               Instant to, Pageable pageable) {
        if (bookableIds.isPresent() && bookableIds.get().isEmpty()) {
            return Page.empty(pageable);
        }
        return bookings.findForAdmin(bookableIds.isEmpty(), bookableIds.orElse(NO_FILTER), status, from, to,
                pageable);
    }
}
