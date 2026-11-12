package com.gridreserve.booking.booking.mapper;

import com.gridreserve.booking.booking.dto.BookingResponse;
import com.gridreserve.booking.booking.entity.Booking;
import com.gridreserve.booking.catalog.dto.BookableInfo;

import java.time.Duration;
import java.time.Instant;

public final class BookingMapper {

    private BookingMapper() {
    }

    public static BookingResponse toResponse(Booking booking, BookableInfo bookable) {
        return new BookingResponse(booking.getId(), booking.getBookableId(), bookable.name(), bookable.label(),
                bookable.venueName(), bookable.venueCity(), bookable.venueCountryCode(), bookable.zone().getId(), booking.getStartTime(), booking.getEndTime(),
                booking.getStatus(), booking.getExpiresAt(), booking.getCreatedAt(), booking.getCancelledAt(),
                booking.getCancelledBy(), booking.getCancelReason(), cancellableUntil(booking, bookable));
    }

    /** Same rule as the cancel UPDATE (CXL-01/02), stated as a moment so the UI can disable Cancel in time. */
    static Instant cancellableUntil(Booking booking, BookableInfo bookable) {
        return switch (booking.getStatus()) {
            case HELD -> booking.getExpiresAt();
            case CONFIRMED -> booking.getStartTime().minus(Duration.ofMinutes(bookable.cancellationCutoffMinutes()));
            case CANCELLED, EXPIRED -> null;
        };
    }
}
