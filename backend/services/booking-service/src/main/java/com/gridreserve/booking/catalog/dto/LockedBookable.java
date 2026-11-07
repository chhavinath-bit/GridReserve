package com.gridreserve.booking.catalog.dto;

import com.gridreserve.booking.catalog.service.EffectiveRules;

/**
 * A bookable read while its venue ({@code FOR SHARE}) and its own row ({@code FOR UPDATE}) are locked, so
 * {@code open} cannot change before the caller's transaction ends. {@code open} = the bookable and its venue are both active.
 * {@code categoryId} scopes the per-member limit (BR-08); the codes label metrics, never decide anything.
 */
public record LockedBookable(BookableInfo info, long categoryId, String categoryCode, String typeCode, boolean open,
                             EffectiveRules rules) {
}
