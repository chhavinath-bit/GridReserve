package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.catalog.dto.BookableInfo;
import com.gridreserve.booking.catalog.dto.LockedBookable;
import com.gridreserve.booking.catalog.entity.BookingModel;
import com.gridreserve.booking.catalog.service.EffectiveRules;
import com.gridreserve.booking.catalog.service.WeeklyHours;
import com.gridreserve.common.web.error.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-07T06:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);
    private static final Instant EARLIER = NOW.minusSeconds(3600);

    private static LockedBookable bookable(boolean open, BookingModel model) {
        EffectiveRules rules = new EffectiveRules(model, 60, 60, 120, 0, 120, 3, 7, ZoneId.of("Asia/Kolkata"),
                new WeeklyHours(Map.of()), false);
        return new LockedBookable(new BookableInfo(1, "Court A", "Court", "Sunrise Turf", rules.zone()), open, rules);
    }

    private static String code(Runnable check) {
        try {
            check.run();
            return null;
        } catch (DomainException ex) {
            return ex.errorCode().code();
        }
    }

    @Test
    @DisplayName("BR-01: end must be after start; equal or reversed → VALIDATION_FAILED")
    void times_BR01() {
        assertThatCode(() -> BookingRules.checkTimes(NOW, LATER)).doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingRules.checkTimes(NOW, NOW)).isInstanceOf(DomainException.class);
        assertThat(code(() -> BookingRules.checkTimes(LATER, NOW)))
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("BR-05: a start at or before now → START_IN_PAST")
    void startInPast_BR05() {
        LockedBookable court = bookable(true, BookingModel.EXCLUSIVE);
        assertThatCode(() -> BookingRules.checkHold(court, LATER, NOW)).doesNotThrowAnyException();
        assertThat(code(() -> BookingRules.checkHold(court, NOW, NOW)))
                .isEqualTo("START_IN_PAST");
    }

    @Test
    @DisplayName("BR-14: the first failing rule wins: BR-06, then BR-09, then BR-05")
    void ruleOrder_BR14() {
        assertThat(
                code(() -> BookingRules.checkHold(bookable(false, BookingModel.CAPACITY), EARLIER, NOW)))
                .isEqualTo("BOOKABLE_INACTIVE");
        assertThat(
                code(() -> BookingRules.checkHold(bookable(true, BookingModel.CAPACITY), EARLIER, NOW)))
                .isEqualTo("WRONG_BOOKING_MODEL");
        assertThat(
                code(() -> BookingRules.checkHold(bookable(true, BookingModel.EXCLUSIVE), EARLIER, NOW)))
                .isEqualTo("START_IN_PAST");
    }
}
