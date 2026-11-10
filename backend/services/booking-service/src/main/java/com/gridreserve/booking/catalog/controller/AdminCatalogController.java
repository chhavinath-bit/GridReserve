package com.gridreserve.booking.catalog.controller;

import com.gridreserve.booking.catalog.dto.ActivationResponse;
import com.gridreserve.booking.catalog.dto.BookableResponse;
import com.gridreserve.booking.catalog.dto.CreateBookableRequest;
import com.gridreserve.booking.catalog.dto.CreateVenueRequest;
import com.gridreserve.booking.catalog.dto.UpdateBookableRequest;
import com.gridreserve.booking.catalog.dto.UpdateVenueRequest;
import com.gridreserve.booking.catalog.dto.VenueResponse;
import com.gridreserve.booking.catalog.service.AdminCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Catalog changes for admins (FR-CAT-07..16, REQUIREMENTS §15.1). The paths sit next to the read endpoints
 * they change; SecurityConfig lets only ADMIN send POST or PUT under {@code /venues} and {@code /bookables}.
 * There is no DELETE: deactivation retires a venue or bookable (FR-CAT-16).
 */
@RestController
@RequestMapping("/api/v1")
public class AdminCatalogController {

    private final AdminCatalogService admin;

    public AdminCatalogController(AdminCatalogService admin) {
        this.admin = admin;
    }

    @PostMapping("/venues")
    public ResponseEntity<VenueResponse> createVenue(@Valid @RequestBody CreateVenueRequest request) {
        VenueResponse venue = admin.createVenue(request);
        return ResponseEntity.created(URI.create("/api/v1/venues/" + venue.id())).body(venue);
    }

    @PutMapping("/venues/{id}")
    public VenueResponse updateVenue(@PathVariable long id, @Valid @RequestBody UpdateVenueRequest request) {
        return admin.updateVenue(id, request);
    }

    @PostMapping("/venues/{id}/deactivate")
    public ActivationResponse deactivateVenue(@PathVariable long id) {
        return admin.setVenueActive(id, false);
    }

    @PostMapping("/venues/{id}/activate")
    public ActivationResponse activateVenue(@PathVariable long id) {
        return admin.setVenueActive(id, true);
    }

    @PostMapping("/venues/{venueId}/bookables")
    public ResponseEntity<BookableResponse> createBookable(@PathVariable long venueId,
                                                           @Valid @RequestBody CreateBookableRequest request) {
        BookableResponse bookable = admin.createBookable(venueId, request);
        return ResponseEntity.created(URI.create("/api/v1/bookables/" + bookable.id())).body(bookable);
    }

    @PutMapping("/bookables/{id}")
    public BookableResponse updateBookable(@PathVariable long id, @Valid @RequestBody UpdateBookableRequest request) {
        return admin.updateBookable(id, request);
    }

    @PostMapping("/bookables/{id}/deactivate")
    public ActivationResponse deactivateBookable(@PathVariable long id) {
        return admin.setBookableActive(id, false);
    }

    @PostMapping("/bookables/{id}/activate")
    public ActivationResponse activateBookable(@PathVariable long id) {
        return admin.setBookableActive(id, true);
    }
}
