package com.gridreserve.booking.catalog;

import com.gridreserve.booking.catalog.dto.OpeningHoursRequest;
import com.gridreserve.booking.catalog.entity.RuleOverrides;
import com.gridreserve.booking.catalog.service.CatalogValidator;
import com.gridreserve.common.web.error.ErrorResponse.FieldError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-CAT-09, the checks that need several fields or the type's defaults. Room type: 30 / 30 / 180. */
class CatalogValidatorTest {

    private static List<String> fields(List<FieldError> errors) {
        return errors.stream().map(FieldError::field).toList();
    }

    private static List<FieldError> room(Integer slot, Integer min, Integer max) {
        return CatalogValidator.rules(30, 30, 180, new RuleOverrides(slot, min, max, null));
    }

    @ParameterizedTest(name = "slot {0}, min {1}, max {2} → valid")
    @CsvSource(nullValues = "-", value = {
            "-, -, -",          // all defaults
            "60, 60, 180",      // whole hours
            "15, 45, 90",       // a finer grid
            "-, 60, 120",       // defaults' 30-minute grid, longer minimum
            "60, 1440, 1440"})  // up to a whole day
    @DisplayName("FR-CAT-09: rules that fit together pass")
    void validRules_FRCAT09(Integer slot, Integer min, Integer max) {
        assertThat(room(slot, min, max)).isEmpty();
    }

    @ParameterizedTest(name = "slot {0}, min {1}, max {2} → error on {3}")
    @CsvSource(nullValues = "-", value = {
            "45, -, -, slotMinutes",             // not 15, 30 or 60
            "60, -, -, minDurationMinutes",       // the type's 30-minute minimum is half a 60-minute slot
            "-, 45, -, minDurationMinutes",       // not a whole number of 30-minute slots
            "-, -, 100, maxDurationMinutes",      // same for the maximum
            "-, 120, 60, maxDurationMinutes",     // minimum above maximum
            "60, 60, 1500, maxDurationMinutes"})  // more than a day
    @DisplayName("FR-CAT-09: each broken rule is reported on the field that must change")
    void brokenRules_FRCAT09(Integer slot, Integer min, Integer max, String field) {
        assertThat(fields(room(slot, min, max))).containsExactly(field);
    }

    @Test
    @DisplayName("FR-CAT-09: a default that no longer fits says it is the type's default")
    void inheritedValueNamed_FRCAT09() {
        assertThat(room(60, null, null).getFirst().message()).contains("type's default").contains("60-minute");
    }

    @Test
    @DisplayName("FR-CAT-09: one interval per weekday, opening before closing; 24:00 may close a day")
    void weeklyHours_FRCAT09() {
        List<OpeningHoursRequest> hours = List.of(
                new OpeningHoursRequest(DayOfWeek.MONDAY, "08:00", "24:00"),
                new OpeningHoursRequest(DayOfWeek.TUESDAY, "18:00", "09:00"),
                new OpeningHoursRequest(DayOfWeek.MONDAY, "10:00", "12:00"),
                new OpeningHoursRequest(DayOfWeek.WEDNESDAY, "09:00", "09:00"));

        assertThat(fields(CatalogValidator.hours("openingHours", hours)))
                .containsExactly("openingHours[1]", "openingHours[2].weekday", "openingHours[3]");
        assertThat(CatalogValidator.hours("openingHours", List.of(new OpeningHoursRequest(DayOfWeek.SUNDAY, "00:00", "24:00"))))
                .isEmpty();
    }

    @Test
    @DisplayName("Times map to minutes from local midnight; 24:00 is 1440")
    void minuteOfDay() {
        assertThat(CatalogValidator.minuteOfDay("00:00")).isZero();
        assertThat(CatalogValidator.minuteOfDay("08:30")).isEqualTo(510);
        assertThat(CatalogValidator.minuteOfDay("24:00")).isEqualTo(1440);
    }
}
