package com.gridreserve.booking.booking.entity;

/** Lifecycle of a booking. HELD and CONFIRMED are "active": only they block a time range. */
public enum BookingStatus {
    HELD,
    CONFIRMED,
    CANCELLED,
    EXPIRED
}
