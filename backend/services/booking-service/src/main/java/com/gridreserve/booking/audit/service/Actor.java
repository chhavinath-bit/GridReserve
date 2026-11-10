package com.gridreserve.booking.audit.service;

import com.gridreserve.booking.audit.entity.ActorType;

/** Who changes a booking: a person (member or admin, by identity user ID) or the system (no user). */
public record Actor(ActorType type, Long userId) {

    public static final Actor SYSTEM = new Actor(ActorType.SYSTEM, null);

    public Actor {
        if ((type == ActorType.SYSTEM) != (userId == null)) {
            throw new IllegalArgumentException("SYSTEM has no user; a member or admin always has one");
        }
    }

    public static Actor person(long userId, boolean admin) {
        return new Actor(admin ? ActorType.ADMIN : ActorType.MEMBER, userId);
    }
}
