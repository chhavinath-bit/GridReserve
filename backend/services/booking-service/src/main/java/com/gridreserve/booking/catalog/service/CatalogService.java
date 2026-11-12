package com.gridreserve.booking.catalog.service;

import com.gridreserve.booking.catalog.dto.BookableInfo;
import com.gridreserve.booking.catalog.dto.BookableResponse;
import com.gridreserve.booking.catalog.dto.BookableSchedule;
import com.gridreserve.booking.catalog.dto.BookableSummaryResponse;
import com.gridreserve.booking.catalog.dto.CategoryResponse;
import com.gridreserve.booking.catalog.dto.CityResponse;
import com.gridreserve.booking.catalog.dto.CountryResponse;
import com.gridreserve.booking.catalog.dto.CountryTimeZonesResponse;
import com.gridreserve.booking.catalog.dto.LockedBookable;
import com.gridreserve.booking.catalog.dto.VenueResponse;
import com.gridreserve.booking.catalog.dto.VenueSummaryResponse;
import com.gridreserve.booking.catalog.entity.Bookable;
import com.gridreserve.booking.catalog.entity.BookableType;
import com.gridreserve.booking.catalog.entity.Venue;
import com.gridreserve.booking.catalog.mapper.CatalogMapper;
import com.gridreserve.booking.catalog.repository.BookableOpeningHoursRepository;
import com.gridreserve.booking.catalog.repository.BookableRepository;
import com.gridreserve.booking.catalog.repository.BookableTypeRepository;
import com.gridreserve.booking.catalog.repository.CategoryRepository;
import com.gridreserve.booking.catalog.repository.CityRepository;
import com.gridreserve.booking.catalog.repository.CountryTimeZoneRepository;
import com.gridreserve.booking.catalog.repository.VenueOpeningHoursRepository;
import com.gridreserve.booking.catalog.repository.VenueRepository;
import com.gridreserve.booking.common.dto.PageResponse;
import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.DomainException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read side of the catalog (FR-CAT-06, FR-CAT-14). Members see only active venues and bookables; an
 * inactive one, or a bookable of an inactive venue, is "not found" for them. {@code includeInactive}
 * is only ever true for admins (the controller decides).
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CategoryRepository categories;
    private final BookableTypeRepository types;
    private final VenueRepository venues;
    private final VenueOpeningHoursRepository venueHours;
    private final BookableRepository bookables;
    private final BookableOpeningHoursRepository bookableHours;
    private final CountryTimeZoneRepository countryZones;
    private final CityRepository cities;
    private final RuleResolver rules;

    public CatalogService(CategoryRepository categories, BookableTypeRepository types, VenueRepository venues,
                          VenueOpeningHoursRepository venueHours, BookableRepository bookables,
                          BookableOpeningHoursRepository bookableHours, CountryTimeZoneRepository countryZones,
                          CityRepository cities, RuleResolver rules) {
        this.categories = categories;
        this.types = types;
        this.venues = venues;
        this.venueHours = venueHours;
        this.bookables = bookables;
        this.bookableHours = bookableHours;
        this.countryZones = countryZones;
        this.cities = cities;
        this.rules = rules;
    }

    /**
     * All categories with their types: two queries, however many there are. Types that can be booked
     * now (EXCLUSIVE) come first, then by label; seed IDs carry no meaningful order.
     */
    public List<CategoryResponse> categories() {
        Map<Long, List<BookableType>> typesByCategory = types.findAll().stream()
                .sorted(Comparator.comparing(BookableType::getBookingModel).thenComparing(BookableType::getLabel))
                .collect(Collectors.groupingBy(type -> type.getCategory().getId()));
        return categories.findAll(Sort.by("id")).stream()
                .map(category -> CatalogMapper.toResponse(category,
                        typesByCategory.getOrDefault(category.getId(), List.of())))
                .toList();
    }

    public PageResponse<VenueSummaryResponse> venues(Long categoryId, String countryCode, Long cityId,
                                                     boolean includeInactive, Pageable pageable) {
        return PageResponse.of(venues.search(categoryId, countryCode, cityId, includeInactive, pageable),
                CatalogMapper::toSummary);
    }

    public VenueResponse venue(long id, boolean includeInactive) {
        Venue venue = visibleVenue(id, includeInactive);
        return CatalogMapper.toResponse(venue, rules.venueHours(venueHours.findByIdVenueId(id)));
    }

    public PageResponse<BookableSummaryResponse> bookables(long venueId, boolean includeInactive, Pageable pageable) {
        visibleVenue(venueId, includeInactive);
        return PageResponse.of(bookables.findByVenue(venueId, includeInactive, pageable), CatalogMapper::toSummary);
    }

    public BookableResponse bookable(long id, boolean includeInactive) {
        Bookable bookable = visibleBookable(id, includeInactive);
        return CatalogMapper.toResponse(bookable, resolve(bookable));
    }

    /** For other modules: the bookable's label and effective rules, with the same visibility rules. */
    public BookableSchedule schedule(long bookableId, boolean includeInactive) {
        Bookable bookable = visibleBookable(bookableId, includeInactive);
        return new BookableSchedule(bookable.getId(), bookable.getBookableType().getLabel(), resolve(bookable));
    }

    /**
     * For hold creation: locks the venue {@code FOR SHARE} (holds on its other bookables go on in
     * parallel), then the bookable {@code FOR UPDATE} (holds on this bookable go one at a time, D-41), in
     * the lock order (CLAUDE.md §8.3), and reads them after the locks are held, so the active flags are current. A lock only
     * means something inside the caller's transaction (it lasts until that ends), so one is required.
     * Inactive bookables are returned, not hidden: the booking module answers {@code 422
     * BOOKABLE_INACTIVE} (BR-06).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LockedBookable lockForBooking(long bookableId) {
        venues.lockForShareByBookable(bookableId).orElseThrow(() -> notFound("No such bookable."));
        bookables.lockForBooking(bookableId);
        Bookable bookable = visibleBookable(bookableId, true);
        BookableType type = bookable.getBookableType();
        return new LockedBookable(info(bookable), type.getCategory().getId(), type.getCategory().getCode(),
                type.getCode(), bookable.isActive() && bookable.getVenue().isActive(), resolve(bookable));
    }

    /** Names for a list of bookings in one query, inactive bookables included (old bookings show them). */
    public Map<Long, BookableInfo> describe(Collection<Long> bookableIds) {
        if (bookableIds.isEmpty()) {
            return Map.of();
        }
        return bookables.findWithVenueByIdIn(bookableIds).stream()
                .map(CatalogService::info)
                .collect(Collectors.toMap(BookableInfo::bookableId, Function.identity()));
    }

    /**
     * The IDs of a category's bookables, for counting a member's bookings in that category (BR-08)
     * without the booking module reading catalog tables. Phase 1 has a few dozen bookables; if that
     * grows a lot, a category column on booking is the alternative (decided 2026-11-07, M1.7).
     */
    public List<Long> bookableIdsInCategory(long categoryId) {
        return bookables.findIdsByCategory(categoryId);
    }

    public List<CountryResponse> countries() {
        return countryZones.findCountryCodes().stream().map(CountryResponse::new).toList();
    }

    public CountryTimeZonesResponse timeZones(String countryCode) {
        String code = countryCode.toUpperCase(Locale.ROOT);
        List<String> zones = countryZones.findByIdCountryCodeOrderByIdTimeZone(code).stream()
                .map(zone -> zone.getTimeZone().getId())
                .toList();
        if (zones.isEmpty()) {
            throw notFound("No such country.");
        }
        return new CountryTimeZonesResponse(code, zones);
    }

    /** The cities of a known country, by name; none yet is an empty list, an unknown country 404 (FR-CAT-16). */
    public List<CityResponse> cities(String countryCode) {
        String code = countryCode.toUpperCase(Locale.ROOT);
        if (countryZones.findByIdCountryCodeOrderByIdTimeZone(code).isEmpty()) {
            throw notFound("No such country.");
        }
        return cities.findByCountryCodeOrderByName(code).stream().map(CatalogMapper::toResponse).toList();
    }

    private EffectiveRules resolve(Bookable bookable) {
        return rules.resolve(bookable, bookableHours.findByIdBookableId(bookable.getId()),
                venueHours.findByIdVenueId(bookable.getVenue().getId()));
    }

    private Venue visibleVenue(long id, boolean includeInactive) {
        return venues.findWithCityById(id)
                .filter(venue -> includeInactive || venue.isActive())
                .orElseThrow(() -> notFound("No such venue."));
    }

    private Bookable visibleBookable(long id, boolean includeInactive) {
        return bookables.findWithRulesById(id)
                .filter(bookable -> includeInactive || (bookable.isActive() && bookable.getVenue().isActive()))
                .orElseThrow(() -> notFound("No such bookable."));
    }

    private static BookableInfo info(Bookable bookable) {
        return new BookableInfo(bookable.getId(), bookable.getName(), bookable.getBookableType().getLabel(),
                bookable.getVenue().getName(), bookable.getVenue().getCity().getName(), bookable.getVenue().getCountryCode(),
                bookable.getVenue().getTimeZone(),
                bookable.getBookableType().getCategory().getCancellationCutoffMinutes());
    }

    private static DomainException notFound(String message) {
        return new DomainException(CommonErrorCode.NOT_FOUND, message);
    }
}
