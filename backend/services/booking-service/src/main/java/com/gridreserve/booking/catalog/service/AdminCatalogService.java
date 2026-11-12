package com.gridreserve.booking.catalog.service;

import com.gridreserve.booking.catalog.dto.ActivationResponse;
import com.gridreserve.booking.catalog.dto.BookableResponse;
import com.gridreserve.booking.catalog.dto.CreateBookableRequest;
import com.gridreserve.booking.catalog.dto.CreateVenueRequest;
import com.gridreserve.booking.catalog.dto.OpeningHoursRequest;
import com.gridreserve.booking.catalog.dto.UpdateBookableRequest;
import com.gridreserve.booking.catalog.dto.UpdateVenueRequest;
import com.gridreserve.booking.catalog.dto.VenueResponse;
import com.gridreserve.booking.catalog.entity.Bookable;
import com.gridreserve.booking.catalog.entity.BookableOpeningHours;
import com.gridreserve.booking.catalog.entity.BookableType;
import com.gridreserve.booking.catalog.entity.BookingModel;
import com.gridreserve.booking.catalog.entity.Category;
import com.gridreserve.booking.catalog.entity.City;
import com.gridreserve.booking.catalog.entity.RuleOverrides;
import com.gridreserve.booking.catalog.entity.Venue;
import com.gridreserve.booking.catalog.entity.VenueOpeningHours;
import com.gridreserve.booking.catalog.repository.BookableOpeningHoursRepository;
import com.gridreserve.booking.catalog.repository.BookableRepository;
import com.gridreserve.booking.catalog.repository.BookableTypeRepository;
import com.gridreserve.booking.catalog.repository.CategoryRepository;
import com.gridreserve.booking.catalog.repository.CityRepository;
import com.gridreserve.booking.catalog.repository.VenueOpeningHoursRepository;
import com.gridreserve.booking.catalog.repository.VenueRepository;
import com.gridreserve.booking.common.exception.BookingErrorCode;
import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.DomainException;
import com.gridreserve.common.web.error.ErrorResponse.FieldError;
import com.gridreserve.common.web.error.FieldValidationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * What admins change in the catalog (FR-CAT-07..16): create and edit venues and bookables, and open or close
 * them for new bookings. Nothing is ever deleted (FR-CAT-16). Edits are optimistic: the request carries the
 * {@code version} the admin read, and a lost race answers {@code 409 STALE_UPDATE} (FR-CAT-15). Opening and
 * closing take the same row locks as hold creation, so a hold and a deactivation never interleave (CONC-09).
 */
@Service
public class AdminCatalogService {

    private static final FieldError NO_SUCH_CITY = new FieldError("cityId", "No such city.");

    private final CategoryRepository categories;
    private final BookableTypeRepository types;
    private final VenueRepository venues;
    private final VenueOpeningHoursRepository venueHours;
    private final BookableRepository bookables;
    private final BookableOpeningHoursRepository bookableHours;
    private final CityRepository cities;
    private final CatalogService catalog;
    private final ScheduledBookings scheduled;
    private final Clock clock;

    public AdminCatalogService(CategoryRepository categories, BookableTypeRepository types, VenueRepository venues,
                               VenueOpeningHoursRepository venueHours, BookableRepository bookables,
                               BookableOpeningHoursRepository bookableHours, CityRepository cities,
                               CatalogService catalog, ScheduledBookings scheduled, Clock clock) {
        this.categories = categories;
        this.types = types;
        this.venues = venues;
        this.venueHours = venueHours;
        this.bookables = bookables;
        this.bookableHours = bookableHours;
        this.cities = cities;
        this.catalog = catalog;
        this.scheduled = scheduled;
        this.clock = clock;
    }

    @Transactional
    public VenueResponse createVenue(CreateVenueRequest request) {
        List<FieldError> errors = new ArrayList<>();
        Category category = categories.findById(request.categoryId()).orElse(null);
        if (category == null) {
            errors.add(new FieldError("categoryId", "No such category."));
        }
        City city = cities.findById(request.cityId()).orElse(null);
        if (city == null) {
            errors.add(NO_SUCH_CITY);
        }
        errors.addAll(CatalogValidator.hours("openingHours", request.openingHours()));
        failIfAny(errors);

        Venue venue = venues.saveAndFlush(Venue.create(category, request.name(), request.addressLine(), city, now()));
        replaceVenueHours(venue.getId(), request.openingHours());
        return catalog.venue(venue.getId(), true);
    }

    @Transactional
    public VenueResponse updateVenue(long id, UpdateVenueRequest request) {
        Venue venue = venues.findById(id).orElseThrow(() -> notFound("No such venue."));
        City city = cities.findById(request.cityId()).orElse(null);
        List<FieldError> errors = new ArrayList<>(city == null ? List.of(NO_SUCH_CITY) : List.of());
        errors.addAll(CatalogValidator.hours("openingHours", request.openingHours()));
        failIfAny(errors);
        checkVersion(venue.getVersion(), request.version());

        venue.update(request.name(), request.addressLine(), city, now());
        flushOrStale(venues::flush);
        replaceVenueHours(id, request.openingHours());
        return catalog.venue(id, true);
    }

    @Transactional
    public BookableResponse createBookable(long venueId, CreateBookableRequest request) {
        Venue venue = venues.findById(venueId).orElseThrow(() -> notFound("No such venue."));
        RuleOverrides overrides = new RuleOverrides(request.slotMinutes(), request.minDurationMinutes(),
                request.maxDurationMinutes(), request.bufferMinutes());
        BookableType type = checkedType(venue, request.bookableTypeId(), overrides, request.openingHours());

        Bookable bookable = bookables.saveAndFlush(Bookable.create(venue, type, request.name(), overrides, now()));
        replaceBookableHours(bookable.getId(), request.openingHours());
        return catalog.bookable(bookable.getId(), true);
    }

    @Transactional
    public BookableResponse updateBookable(long id, UpdateBookableRequest request) {
        Bookable bookable = bookables.findWithRulesById(id).orElseThrow(() -> notFound("No such bookable."));
        RuleOverrides overrides = new RuleOverrides(request.slotMinutes(), request.minDurationMinutes(),
                request.maxDurationMinutes(), request.bufferMinutes());
        BookableType type = checkedType(bookable.getVenue(), request.bookableTypeId(), overrides,
                request.openingHours());
        checkVersion(bookable.getVersion(), request.version());

        bookable.update(type, request.name(), overrides, now());
        flushOrStale(bookables::flush);
        replaceBookableHours(id, request.openingHours());
        return catalog.bookable(id, true);
    }

    /**
     * Soft close or reopen (FR-CAT-10/11): locks the bookable {@code FOR UPDATE}, the lock a hold takes
     * (D-41), so a hold either committed before (and stays valid) or sees the bookable inactive (CONC-09).
     * Existing bookings are never touched.
     */
    @Transactional
    public ActivationResponse setBookableActive(long id, boolean active) {
        bookables.lockForBooking(id).orElseThrow(() -> notFound("No such bookable."));
        Bookable bookable = bookables.findById(id).orElseThrow(() -> notFound("No such bookable."));
        Instant now = now();
        bookable.changeActive(active, now);
        bookables.flush();
        return new ActivationResponse(id, bookable.isActive(), bookable.getVersion(),
                scheduled.activeFuture(List.of(id), now));
    }

    /**
     * Soft close or reopen a venue (FR-CAT-13): locks it {@code FOR UPDATE} against holds, which hold it {@code
     * FOR SHARE}. Its bookables keep their own flags, so reopening brings back exactly those that were open.
     */
    @Transactional
    public ActivationResponse setVenueActive(long id, boolean active) {
        venues.lockForUpdate(id).orElseThrow(() -> notFound("No such venue."));
        Venue venue = venues.findById(id).orElseThrow(() -> notFound("No such venue."));
        Instant now = now();
        venue.changeActive(active, now);
        venues.flush();
        return new ActivationResponse(id, venue.isActive(), venue.getVersion(),
                scheduled.activeFuture(bookables.findIdsByVenue(id), now));
    }

    /** A venue's bookables, inactive ones included, for the admin booking list's venue filter (FR-ADM-01). */
    @Transactional(readOnly = true)
    public List<Long> bookableIdsAt(long venueId) {
        return bookables.findIdsByVenue(venueId);
    }

    private BookableType checkedType(Venue venue, long typeId, RuleOverrides overrides,
                                     List<OpeningHoursRequest> hours) {
        List<FieldError> errors = new ArrayList<>();
        BookableType type = types.findById(typeId)
                .filter(found -> found.getCategory().getId().equals(venue.getCategory().getId()))
                .orElse(null);
        if (type == null) {
            errors.add(new FieldError("bookableTypeId", "No such bookable type in this venue's category."));
        } else if (type.getBookingModel() == BookingModel.EXCLUSIVE) {
            errors.addAll(CatalogValidator.rules(type.getSlotMinutes(), type.getMinDurationMinutes(),
                    type.getMaxDurationMinutes(), overrides));
        }
        if (hours != null) {
            errors.addAll(CatalogValidator.hours("openingHours", hours));
        }
        failIfAny(errors);
        if (type.getBookingModel() != BookingModel.EXCLUSIVE) {
            throw new DomainException(BookingErrorCode.WRONG_BOOKING_MODEL,
                    "Only time-slot (EXCLUSIVE) bookables can be added until seat sessions arrive in Phase 2.");
        }
        return type;
    }

    // Weekly hours are replaced as a whole: delete in one statement, then insert the new days.
    private void replaceVenueHours(long venueId, List<OpeningHoursRequest> hours) {
        venueHours.deleteAllFor(venueId);
        venueHours.saveAllAndFlush(hours.stream().map(day -> new VenueOpeningHours(venueId, day.weekday(),
                CatalogValidator.minuteOfDay(day.opensAt()), CatalogValidator.minuteOfDay(day.closesAt()))).toList());
    }

    // null keeps no own hours: the bookable follows its venue's (FR-CAT-04).
    private void replaceBookableHours(long bookableId, List<OpeningHoursRequest> hours) {
        bookableHours.deleteAllFor(bookableId);
        if (hours != null) {
            bookableHours.saveAllAndFlush(hours.stream().map(day -> new BookableOpeningHours(bookableId,
                    day.weekday(), CatalogValidator.minuteOfDay(day.opensAt()),
                    CatalogValidator.minuteOfDay(day.closesAt()))).toList());
        }
    }

    private static void checkVersion(long current, long expected) {
        if (current != expected) {
            throw stale();
        }
    }

    // Two admins who read the same version both pass checkVersion; the second UPDATE then matches no row
    // (WHERE version = ?), and Hibernate reports it here.
    private static void flushOrStale(Runnable flush) {
        try {
            flush.run();
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw stale();
        }
    }

    private static DomainException stale() {
        return new DomainException(BookingErrorCode.STALE_UPDATE,
                "Someone else changed this in the meantime. Reload it and try again.");
    }

    private static void failIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static DomainException notFound(String message) {
        return new DomainException(CommonErrorCode.NOT_FOUND, message);
    }
}
