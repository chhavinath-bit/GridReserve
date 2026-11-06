package com.gridreserve.booking.catalog.service;

import com.gridreserve.booking.catalog.entity.Bookable;
import com.gridreserve.booking.catalog.entity.BookableOpeningHours;
import com.gridreserve.booking.catalog.entity.BookableType;
import com.gridreserve.booking.catalog.entity.Category;
import com.gridreserve.booking.catalog.entity.VenueOpeningHours;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The one place that decides which rule value applies to a bookable (FR-CAT-05):
 * <ul>
 *   <li>slot, durations, buffer: the bookable's override, else its type's default;</li>
 *   <li>cutoff, member limit, horizon: the category's value;</li>
 *   <li>opening hours: the bookable's own weekly hours if it has any (they replace the venue's
 *       hours entirely, no per-day mixing, FR-CAT-04), else the venue's.</li>
 * </ul>
 * Category differences are data: there is no {@code if} on a category or type code here or anywhere.
 */
@Component
public class RuleResolver {

    public EffectiveRules resolve(Bookable bookable, List<BookableOpeningHours> ownHours,
                                  List<VenueOpeningHours> venueHours) {
        BookableType type = bookable.getBookableType();
        Category category = type.getCategory();
        boolean hasOwnHours = !ownHours.isEmpty();
        WeeklyHours hours = hasOwnHours ? fromBookable(ownHours) : fromVenue(venueHours);

        return new EffectiveRules(
                type.getBookingModel(),
                firstNonNull(bookable.getSlotMinutes(), type.getSlotMinutes()),
                firstNonNull(bookable.getMinDurationMinutes(), type.getMinDurationMinutes()),
                firstNonNull(bookable.getMaxDurationMinutes(), type.getMaxDurationMinutes()),
                firstNonNull(bookable.getBufferMinutes(), type.getBufferMinutes()),
                category.getCancellationCutoffMinutes(),
                category.getMaxActiveBookingsPerMember(),
                category.getBookingHorizonDays(),
                bookable.getVenue().getTimeZone(),
                hours,
                hasOwnHours);
    }

    /** A venue's own weekly hours, for showing the venue. */
    public WeeklyHours venueHours(List<VenueOpeningHours> rows) {
        return fromVenue(rows);
    }

    private static WeeklyHours fromVenue(List<VenueOpeningHours> rows) {
        Map<DayOfWeek, OpeningInterval> days = new EnumMap<>(DayOfWeek.class);
        rows.forEach(row -> days.put(row.getDayOfWeek(),
                new OpeningInterval(row.getOpensAtMinute(), row.getClosesAtMinute())));
        return new WeeklyHours(days);
    }

    private static WeeklyHours fromBookable(List<BookableOpeningHours> rows) {
        Map<DayOfWeek, OpeningInterval> days = new EnumMap<>(DayOfWeek.class);
        rows.forEach(row -> days.put(row.getDayOfWeek(),
                new OpeningInterval(row.getOpensAtMinute(), row.getClosesAtMinute())));
        return new WeeklyHours(days);
    }

    private static <T> T firstNonNull(T override, T fallback) {
        return override != null ? override : fallback;
    }
}
