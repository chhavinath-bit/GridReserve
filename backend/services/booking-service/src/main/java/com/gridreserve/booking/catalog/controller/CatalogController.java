package com.gridreserve.booking.catalog.controller;

import com.gridreserve.booking.catalog.dto.BookableResponse;
import com.gridreserve.booking.catalog.dto.BookableSummaryResponse;
import com.gridreserve.booking.catalog.dto.CategoryResponse;
import com.gridreserve.booking.catalog.dto.CountryResponse;
import com.gridreserve.booking.catalog.dto.CountryTimeZonesResponse;
import com.gridreserve.booking.catalog.dto.VenueResponse;
import com.gridreserve.booking.catalog.dto.VenueSummaryResponse;
import com.gridreserve.booking.catalog.service.CatalogService;
import com.gridreserve.booking.common.dto.PageResponse;
import com.gridreserve.booking.common.web.PageParams;
import com.gridreserve.booking.common.web.Viewer;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Catalog reads for any signed-in user (FR-CAT-06, FR-CAT-14). {@code includeInactive=true} counts
 * only for admins; for members it is ignored, so they never see closed venues or bookables.
 */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalog.categories();
    }

    @GetMapping("/venues")
    public PageResponse<VenueSummaryResponse> venues(@RequestParam(required = false) Long categoryId,
                                                     @RequestParam(required = false) String country,
                                                     @RequestParam(required = false) String city,
                                                     @RequestParam(defaultValue = "false") boolean includeInactive,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = PageParams.DEFAULT_SIZE) int size,
                                                     Authentication caller) {
        return catalog.venues(categoryId, blankToNull(country, true), blankToNull(city, false),
                includeInactive && Viewer.isAdmin(caller),
                PageParams.of(page, size, Sort.by("name", "id")));
    }

    @GetMapping("/venues/{id}")
    public VenueResponse venue(@PathVariable long id, Authentication caller) {
        return catalog.venue(id, Viewer.isAdmin(caller));
    }

    @GetMapping("/venues/{venueId}/bookables")
    public PageResponse<BookableSummaryResponse> bookables(@PathVariable long venueId,
                                                           @RequestParam(defaultValue = "false") boolean includeInactive,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = PageParams.DEFAULT_SIZE) int size,
                                                           Authentication caller) {
        return catalog.bookables(venueId, includeInactive && Viewer.isAdmin(caller),
                PageParams.of(page, size, Sort.by("id")));
    }

    @GetMapping("/bookables/{id}")
    public BookableResponse bookable(@PathVariable long id, Authentication caller) {
        return catalog.bookable(id, Viewer.isAdmin(caller));
    }

    @GetMapping("/countries")
    public List<CountryResponse> countries() {
        return catalog.countries();
    }

    @GetMapping("/countries/{code}/time-zones")
    public CountryTimeZonesResponse timeZones(@PathVariable String code) {
        return catalog.timeZones(code);
    }

    // An empty filter (?city=) means "any", like a missing one; countries are stored upper case ("in" finds IN).
    private static String blankToNull(String value, boolean upperCase) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return upperCase ? trimmed.toUpperCase(Locale.ROOT) : trimmed;
    }
}
