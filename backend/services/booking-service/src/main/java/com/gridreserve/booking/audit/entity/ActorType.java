package com.gridreserve.booking.audit.entity;

/** Who made a booking change (FR-AUD-01): a member, an admin, or the system (hold expiry). */
public enum ActorType {
    MEMBER,
    ADMIN,
    SYSTEM
}
