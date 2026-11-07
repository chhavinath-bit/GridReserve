package com.gridreserve.booking.catalog.dto;

import com.gridreserve.booking.catalog.service.EffectiveRules;

/**
 * A bookable read while its venue and row are locked {@code FOR SHARE}, so {@code open} cannot change
 * before the caller's transaction ends. {@code open} = the bookable and its venue are both active.
 */
public record LockedBookable(BookableInfo info, boolean open, EffectiveRules rules) {
}
