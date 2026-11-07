package com.gridreserve.booking.catalog;

import com.gridreserve.booking.catalog.entity.Bookable;
import com.gridreserve.booking.catalog.entity.BookableOpeningHours;
import com.gridreserve.booking.catalog.entity.BookableType;
import com.gridreserve.booking.catalog.entity.BookingModel;
import com.gridreserve.booking.catalog.entity.Category;
import com.gridreserve.booking.catalog.entity.Venue;
import com.gridreserve.booking.catalog.entity.VenueOpeningHours;
import com.gridreserve.booking.catalog.service.EffectiveRules;
import com.gridreserve.booking.catalog.service.OpeningInterval;
import com.gridreserve.booking.catalog.service.RuleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** FR-CAT-05: which value wins at each level. Entities are Mockito stand-ins, no database. */
class RuleResolverTest {

    private final RuleResolver resolver = new RuleResolver();

    private Bookable bookable;
    private final List<VenueOpeningHours> venueHours = List.of(venueHours(DayOfWeek.MONDAY, 480, 1320));

    @BeforeEach
    void setUp() {
        Category category = mock(Category.class);
        when(category.getCancellationCutoffMinutes()).thenReturn(120);
        when(category.getMaxActiveBookingsPerMember()).thenReturn(3);
        when(category.getBookingHorizonDays()).thenReturn(7);

        BookableType type = mock(BookableType.class);
        when(type.getCategory()).thenReturn(category);
        when(type.getBookingModel()).thenReturn(BookingModel.EXCLUSIVE);
        when(type.getSlotMinutes()).thenReturn(15);
        when(type.getMinDurationMinutes()).thenReturn(45);
        when(type.getMaxDurationMinutes()).thenReturn(45);
        when(type.getBufferMinutes()).thenReturn(15);

        Venue venue = mock(Venue.class);
        when(venue.getTimeZone()).thenReturn(ZoneId.of("Europe/London"));

        bookable = mock(Bookable.class);
        when(bookable.getBookableType()).thenReturn(type);
        when(bookable.getVenue()).thenReturn(venue);
        // No overrides (null = inherit). Mockito would otherwise answer 0 for an Integer getter.
        when(bookable.getSlotMinutes()).thenReturn(null);
        when(bookable.getMinDurationMinutes()).thenReturn(null);
        when(bookable.getMaxDurationMinutes()).thenReturn(null);
        when(bookable.getBufferMinutes()).thenReturn(null);
    }

    @Test
    @DisplayName("FR-CAT-05: without overrides, the type's defaults and the category's values apply")
    void typeDefaults() {
        EffectiveRules rules = resolver.resolve(bookable, List.of(), venueHours);

        assertThat(rules.slotMinutes()).isEqualTo(15);
        assertThat(rules.minDurationMinutes()).isEqualTo(45);
        assertThat(rules.maxDurationMinutes()).isEqualTo(45);
        assertThat(rules.bufferMinutes()).isEqualTo(15);
        assertThat(rules.cancellationCutoffMinutes()).isEqualTo(120);
        assertThat(rules.maxActiveBookingsPerMember()).isEqualTo(3);
        assertThat(rules.bookingHorizonDays()).isEqualTo(7);
        assertThat(rules.zone()).isEqualTo(ZoneId.of("Europe/London"));
    }

    @Test
    @DisplayName("FR-CAT-04/05: each bookable override wins over the type default, one by one")
    void bookableOverrides() {
        when(bookable.getSlotMinutes()).thenReturn(30);
        when(bookable.getMaxDurationMinutes()).thenReturn(90);
        when(bookable.getBufferMinutes()).thenReturn(0);

        EffectiveRules rules = resolver.resolve(bookable, List.of(), venueHours);

        assertThat(rules.slotMinutes()).isEqualTo(30);
        assertThat(rules.minDurationMinutes()).isEqualTo(45); // not overridden: the type's value
        assertThat(rules.maxDurationMinutes()).isEqualTo(90);
        assertThat(rules.bufferMinutes()).isZero();          // an override of 0 still counts
    }

    @Test
    @DisplayName("FR-CAT-04: without own hours, the venue's hours apply")
    void venueHoursByDefault() {
        EffectiveRules rules = resolver.resolve(bookable, List.of(), venueHours);

        assertThat(rules.ownHours()).isFalse();
        assertThat(rules.hours().on(DayOfWeek.MONDAY)).contains(new OpeningInterval(480, 1320));
        assertThat(rules.hours().on(DayOfWeek.SUNDAY)).isEmpty();
    }

    @Test
    @DisplayName("FR-CAT-04: own hours replace the venue's entirely, with no per-day mixing")
    void ownHoursReplaceVenueHours() {
        List<BookableOpeningHours> own = List.of(bookableHours(DayOfWeek.TUESDAY, 600, 840));

        EffectiveRules rules = resolver.resolve(bookable, own, venueHours);

        assertThat(rules.ownHours()).isTrue();
        assertThat(rules.hours().on(DayOfWeek.TUESDAY)).contains(new OpeningInterval(600, 840));
        assertThat(rules.hours().on(DayOfWeek.MONDAY)).isEmpty(); // the venue's Monday does not leak in
    }

    private static VenueOpeningHours venueHours(DayOfWeek day, int opens, int closes) {
        VenueOpeningHours row = mock(VenueOpeningHours.class);
        when(row.getDayOfWeek()).thenReturn(day);
        when(row.getOpensAtMinute()).thenReturn(opens);
        when(row.getClosesAtMinute()).thenReturn(closes);
        return row;
    }

    private static BookableOpeningHours bookableHours(DayOfWeek day, int opens, int closes) {
        BookableOpeningHours row = mock(BookableOpeningHours.class);
        when(row.getDayOfWeek()).thenReturn(day);
        when(row.getOpensAtMinute()).thenReturn(opens);
        when(row.getClosesAtMinute()).thenReturn(closes);
        return row;
    }
}
