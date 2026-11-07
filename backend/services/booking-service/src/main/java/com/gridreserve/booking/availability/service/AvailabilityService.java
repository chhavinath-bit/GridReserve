package com.gridreserve.booking.availability.service;

import com.gridreserve.booking.availability.dto.AvailabilityResponse;
import com.gridreserve.booking.availability.dto.SlotResponse;
import com.gridreserve.booking.availability.dto.SlotStatus;
import com.gridreserve.booking.booking.dto.OccupiedRange;
import com.gridreserve.booking.booking.service.BookingOccupancy;
import com.gridreserve.booking.catalog.dto.BookableSchedule;
import com.gridreserve.booking.catalog.entity.BookingModel;
import com.gridreserve.booking.catalog.service.CatalogService;
import com.gridreserve.booking.catalog.service.EffectiveRules;
import com.gridreserve.booking.catalog.service.OpeningInterval;
import com.gridreserve.booking.common.exception.BookingErrorCode;
import com.gridreserve.common.web.error.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Which slots of a bookable are free on a local date (FR-AV-01..06). Read-only: it never locks or
 * writes, so the answer can be out of date a moment later. The booking itself is decided by the
 * database constraint (M1.5), never by this view.
 */
@Service
public class AvailabilityService {

    private final CatalogService catalog;
    private final BookingOccupancy occupancy;
    private final Clock clock;

    public AvailabilityService(CatalogService catalog, BookingOccupancy occupancy, Clock clock) {
        this.catalog = catalog;
        this.occupancy = occupancy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse forDate(long bookableId, LocalDate date, boolean includeInactive) {
        BookableSchedule schedule = catalog.schedule(bookableId, includeInactive);
        EffectiveRules rules = schedule.rules();
        if (rules.bookingModel() != BookingModel.EXCLUSIVE) {
            throw new DomainException(BookingErrorCode.WRONG_BOOKING_MODEL,
                    "This can't be booked by time slot yet.");
        }

        Instant now = clock.instant();
        // Whole local days (D-39): the same horizon hold creation checks (BR-07).
        if (!date.isBefore(rules.firstDateBeyondHorizon(now))) {
            throw new DomainException(BookingErrorCode.BEYOND_HORIZON,
                    "Bookings open " + rules.bookingHorizonDays() + " days ahead.");
        }

        Optional<OpeningInterval> hours = rules.hours().on(date.getDayOfWeek());
        if (hours.isEmpty()) {
            return response(schedule, date, true, List.of());
        }

        List<LocalSlot> grid = LocalSlotGrid.slots(date, hours.get(), rules.slotMinutes(), rules.zone());
        if (grid.isEmpty()) {
            return response(schedule, date, false, List.of());
        }
        List<OccupiedRange> occupied = occupancy.occupied(bookableId, grid.getFirst().start(),
                grid.getLast().end(), now);

        List<SlotResponse> slots = grid.stream()
                .map(slot -> new SlotResponse(slot.start(), slot.end(), slot.localStart(), slot.localEnd(),
                        status(slot, occupied, now)))
                .toList();
        return response(schedule, date, false, slots);
    }

    private static SlotStatus status(LocalSlot slot, List<OccupiedRange> occupied, Instant now) {
        if (!slot.start().isAfter(now)) {
            return SlotStatus.PAST;
        }
        boolean taken = occupied.stream().anyMatch(range -> range.overlaps(slot.start(), slot.end()));
        return taken ? SlotStatus.TAKEN : SlotStatus.FREE;
    }

    private static AvailabilityResponse response(BookableSchedule schedule, LocalDate date, boolean closed,
                                                 List<SlotResponse> slots) {
        EffectiveRules rules = schedule.rules();
        return new AvailabilityResponse(schedule.bookableId(), date, rules.zone().getId(), schedule.label(),
                rules.slotMinutes(), rules.minDurationMinutes(), rules.maxDurationMinutes(), rules.bufferMinutes(),
                closed, slots);
    }
}
