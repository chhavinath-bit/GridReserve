package com.gridreserve.booking.catalog.entity;

/** How a bookable is booked: one booking per time range, or N seats per session (Phase 2). */
public enum BookingModel {
    EXCLUSIVE,
    CAPACITY
}
