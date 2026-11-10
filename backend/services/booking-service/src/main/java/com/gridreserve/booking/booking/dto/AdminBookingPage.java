package com.gridreserve.booking.booking.dto;

import java.util.List;

/**
 * A page of the admin booking list, in the standard page shape (API-04) plus {@code memberDetailsAvailable}:
 * false when identity-service was down or its circuit was open, so the list shows member IDs only (RES-06,
 * FS-33) instead of failing.
 */
public record AdminBookingPage(List<AdminBookingResponse> items, int page, int size, long totalItems,
                               int totalPages, boolean memberDetailsAvailable) {
}
