package com.gridreserve.booking.booking.dto;

/**
 * One booking in the admin list (FR-ADM-01): the booking as members see it, plus who booked it. Email and
 * display name come from identity-service at read time; both are null when it couldn't be reached
 * (the page then says {@code memberDetailsAvailable: false}) or the user no longer exists.
 */
public record AdminBookingResponse(BookingResponse booking, long memberId, String memberEmail,
                                   String memberDisplayName) {
}
