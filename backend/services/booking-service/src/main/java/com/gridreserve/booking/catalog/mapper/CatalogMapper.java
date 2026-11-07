package com.gridreserve.booking.catalog.mapper;

import com.gridreserve.booking.catalog.dto.BookableResponse;
import com.gridreserve.booking.catalog.dto.BookableSummaryResponse;
import com.gridreserve.booking.catalog.dto.BookableTypeResponse;
import com.gridreserve.booking.catalog.dto.CategoryResponse;
import com.gridreserve.booking.catalog.dto.OpeningHoursResponse;
import com.gridreserve.booking.catalog.dto.RulesResponse;
import com.gridreserve.booking.catalog.dto.VenueResponse;
import com.gridreserve.booking.catalog.dto.VenueSummaryResponse;
import com.gridreserve.booking.catalog.entity.Bookable;
import com.gridreserve.booking.catalog.entity.BookableType;
import com.gridreserve.booking.catalog.entity.Category;
import com.gridreserve.booking.catalog.entity.Venue;
import com.gridreserve.booking.catalog.service.EffectiveRules;
import com.gridreserve.booking.catalog.service.OpeningInterval;
import com.gridreserve.booking.catalog.service.WeeklyHours;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;

/** Entity → response DTO, by hand (no MapStruct). Entities never leave the service (API-02). */
public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static CategoryResponse toResponse(Category category, List<BookableType> types) {
        return new CategoryResponse(category.getId(), category.getCode(), category.getName(),
                category.getCancellationCutoffMinutes(), category.getMaxActiveBookingsPerMember(),
                category.getBookingHorizonDays(), types.stream().map(CatalogMapper::toResponse).toList());
    }

    public static BookableTypeResponse toResponse(BookableType type) {
        return new BookableTypeResponse(type.getId(), type.getCode(), type.getLabel(), type.getBookingModel(),
                type.getSlotMinutes(), type.getMinDurationMinutes(), type.getMaxDurationMinutes(),
                type.getBufferMinutes());
    }

    public static VenueSummaryResponse toSummary(Venue venue) {
        return new VenueSummaryResponse(venue.getId(), venue.getCategory().getId(), venue.getName(),
                venue.getAddressLine(), venue.getCountryCode(), venue.getTimeZone().getId(), venue.isActive());
    }

    public static VenueResponse toResponse(Venue venue, WeeklyHours hours) {
        return new VenueResponse(venue.getId(), venue.getCategory().getId(), venue.getName(),
                venue.getAddressLine(), venue.getCountryCode(), venue.getTimeZone().getId(), venue.isActive(),
                toResponse(hours));
    }

    public static BookableSummaryResponse toSummary(Bookable bookable) {
        BookableType type = bookable.getBookableType();
        return new BookableSummaryResponse(bookable.getId(), bookable.getVenue().getId(), bookable.getName(),
                type.getCode(), type.getLabel(), type.getBookingModel(), bookable.isActive());
    }

    public static BookableResponse toResponse(Bookable bookable, EffectiveRules rules) {
        BookableType type = bookable.getBookableType();
        Venue venue = bookable.getVenue();
        return new BookableResponse(bookable.getId(), venue.getId(), venue.getName(), venue.getTimeZone().getId(),
                bookable.getName(), type.getCode(), type.getLabel(), type.getBookingModel(), bookable.isActive(),
                toResponse(rules), toResponse(rules.hours()), rules.ownHours());
    }

    public static RulesResponse toResponse(EffectiveRules rules) {
        return new RulesResponse(rules.slotMinutes(), rules.minDurationMinutes(), rules.maxDurationMinutes(),
                rules.bufferMinutes(), rules.cancellationCutoffMinutes(), rules.maxActiveBookingsPerMember(),
                rules.bookingHorizonDays());
    }

    /** Open weekdays only, Monday first. */
    public static List<OpeningHoursResponse> toResponse(WeeklyHours hours) {
        return Arrays.stream(DayOfWeek.values())
                .flatMap(day -> hours.on(day).stream().map(interval -> toResponse(day, interval)))
                .toList();
    }

    private static OpeningHoursResponse toResponse(DayOfWeek day, OpeningInterval interval) {
        return new OpeningHoursResponse(day, OpeningInterval.format(interval.opensAtMinute()),
                OpeningInterval.format(interval.closesAtMinute()));
    }
}
