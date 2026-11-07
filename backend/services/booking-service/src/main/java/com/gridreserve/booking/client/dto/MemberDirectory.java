package com.gridreserve.booking.client.dto;

import java.util.Map;
import java.util.Optional;

/**
 * Member names and emails for a set of IDs, or none at all when identity-service couldn't be reached
 * ({@code available = false}, RES-06): callers then show member IDs only and say so.
 */
public record MemberDirectory(boolean available, Map<Long, IdentityUser> members) {

    public static MemberDirectory unavailable() {
        return new MemberDirectory(false, Map.of());
    }

    public Optional<IdentityUser> find(long memberId) {
        return Optional.ofNullable(members.get(memberId));
    }
}
