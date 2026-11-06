package com.gridreserve.booking.availability.controller;

import com.gridreserve.booking.availability.dto.AvailabilityResponse;
import com.gridreserve.booking.availability.service.AvailabilityService;
import com.gridreserve.booking.common.web.Viewer;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class AvailabilityController {

    private final AvailabilityService availability;

    public AvailabilityController(AvailabilityService availability) {
        this.availability = availability;
    }

    /** {@code date} is a local date at the venue, e.g. {@code 2026-11-07} (API-03). */
    @GetMapping("/api/v1/bookables/{id}/availability")
    public AvailabilityResponse availability(@PathVariable long id,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                             Authentication caller) {
        return availability.forDate(id, date, Viewer.isAdmin(caller));
    }
}
