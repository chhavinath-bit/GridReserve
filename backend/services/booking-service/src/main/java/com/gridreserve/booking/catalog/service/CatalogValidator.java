package com.gridreserve.booking.catalog.service;

import com.gridreserve.booking.catalog.dto.OpeningHoursRequest;
import com.gridreserve.booking.catalog.entity.RuleOverrides;
import com.gridreserve.common.web.error.ErrorResponse.FieldError;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The admin checks of FR-CAT-09 that Bean Validation can't express, because they look at several fields
 * together or at the bookable type's defaults. Each method returns field errors (empty = valid); the service
 * collects them all and answers once, so an admin sees every problem at the same time.
 */
public final class CatalogValidator {

    static final Set<Integer> SLOT_LENGTHS = Set.of(15, 30, 60);
    static final int MAX_DURATION_MINUTES = 24 * 60;

    private CatalogValidator() {
    }

    /** Weekly hours: each weekday at most once, and every interval opens before it closes. */
    public static List<FieldError> hours(String field, List<OpeningHoursRequest> hours) {
        List<FieldError> errors = new ArrayList<>();
        Set<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
        for (int i = 0; i < hours.size(); i++) {
            OpeningHoursRequest day = hours.get(i);
            if (!seen.add(day.weekday())) {
                errors.add(new FieldError(field + "[" + i + "].weekday", day.weekday() + " appears more than once."));
            }
            if (minuteOfDay(day.opensAt()) >= minuteOfDay(day.closesAt())) {
                errors.add(new FieldError(field + "[" + i + "]", "Must open before it closes."));
            }
        }
        return errors;
    }

    /**
     * The rules a bookable ends up with (its overrides, else the type's defaults) must fit together: slot
     * 15, 30 or 60 minutes; slot <= min <= max <= 24 h; min and max whole multiples of the slot. A default
     * that no longer fits an override is reported on the field that would have to change. The defaults are
     * the bookable type's (an EXCLUSIVE type always has all three).
     */
    public static List<FieldError> rules(int defaultSlot, int defaultMin, int defaultMax, RuleOverrides overrides) {
        List<FieldError> errors = new ArrayList<>();
        if (overrides.slotMinutes() != null && !SLOT_LENGTHS.contains(overrides.slotMinutes())) {
            errors.add(new FieldError("slotMinutes", "Must be 15, 30 or 60."));
            return errors;
        }
        int slot = firstNonNull(overrides.slotMinutes(), defaultSlot);
        int min = firstNonNull(overrides.minDurationMinutes(), defaultMin);
        int max = firstNonNull(overrides.maxDurationMinutes(), defaultMax);
        if (min % slot != 0) {
            errors.add(new FieldError("minDurationMinutes",
                    describe(min, overrides.minDurationMinutes()) + " must be a whole number of " + slot + "-minute slots."));
        }
        if (max % slot != 0) {
            errors.add(new FieldError("maxDurationMinutes",
                    describe(max, overrides.maxDurationMinutes()) + " must be a whole number of " + slot + "-minute slots."));
        }
        if (min > max) {
            errors.add(new FieldError("maxDurationMinutes", "Must be at least the minimum duration (" + min + ")."));
        }
        if (max > MAX_DURATION_MINUTES) {
            errors.add(new FieldError("maxDurationMinutes", "Must be at most 24 hours (1440)."));
        }
        return errors;
    }

    /** "08:30" → 510, "24:00" → 1440. The format was checked by Bean Validation. */
    public static int minuteOfDay(String time) {
        return Integer.parseInt(time.substring(0, 2)) * 60 + Integer.parseInt(time.substring(3, 5));
    }

    private static String describe(int value, Integer override) {
        return override != null ? "The duration (" + value + ")" : "The type's default duration (" + value + ")";
    }

    private static int firstNonNull(Integer override, Integer fallback) {
        return override != null ? override : fallback;
    }
}
