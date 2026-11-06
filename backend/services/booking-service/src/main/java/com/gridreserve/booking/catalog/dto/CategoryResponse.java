package com.gridreserve.booking.catalog.dto;

import java.util.List;

public record CategoryResponse(
        long id,
        String code,
        String name,
        int cancellationCutoffMinutes,
        int maxActiveBookingsPerMember,
        int bookingHorizonDays,
        List<BookableTypeResponse> bookableTypes) {
}
