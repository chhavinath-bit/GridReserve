package com.gridreserve.booking.booking.mapper;

import com.gridreserve.booking.booking.dto.BookingResponse;
import com.gridreserve.booking.booking.entity.Booking;
import com.gridreserve.booking.catalog.dto.BookableInfo;

public final class BookingMapper {

    private BookingMapper() {
    }

    public static BookingResponse toResponse(Booking booking, BookableInfo bookable) {
        return new BookingResponse(booking.getId(), booking.getBookableId(), bookable.name(), bookable.label(),
                bookable.venueName(), bookable.zone().getId(), booking.getStartTime(), booking.getEndTime(),
                booking.getStatus(), booking.getExpiresAt(), booking.getCreatedAt(), booking.getCancelledAt(),
                booking.getCancelledBy());
    }
}
