package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.catalog.dto.BookableInfo;
import com.gridreserve.booking.catalog.dto.LockedBookable;
import com.gridreserve.booking.catalog.entity.BookingModel;
import com.gridreserve.booking.catalog.service.EffectiveRules;
import com.gridreserve.booking.catalog.service.OpeningInterval;
import com.gridreserve.booking.catalog.service.WeeklyHours;
import com.gridreserve.common.web.error.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every booking rule (BR-01..09, BR-14) against the seeded type defaults, without a database. "Now" is
 * Monday 9 Nov 2026, 08:00 in Kolkata; the horizon (7 days) runs to Sunday 15 Nov.
 */
class BookingRulesTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final Instant NOW = at("2026-11-09T08:00", KOLKATA);

    private static Instant at(String localDateTime, ZoneId zone) {
        return LocalDateTime.parse(localDateTime).atZone(zone).toInstant();
    }

    private static WeeklyHours hours(String opens, String closes, DayOfWeek... closedDays) {
        Map<DayOfWeek, OpeningInterval> days = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            days.put(day, new OpeningInterval(minutes(opens), minutes(closes)));
        }
        for (DayOfWeek closed : closedDays) {
            days.remove(closed);
        }
        return new WeeklyHours(days);
    }

    private static int minutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3));
    }

    private static EffectiveRules rules(String type, ZoneId zone) {
        return switch (type) {
            // slot / min / max / buffer from the seed (CLAUDE.md §15.1); hours as the demo venues have them.
            case "ROOM" -> rules(30, 30, 180, 0, hours("08:00", "22:00"), zone);
            case "SEAT" -> rules(60, 60, 240, 0, hours("08:00", "22:00"), zone);
            case "DOCTOR" -> rules(30, 30, 30, 0, hours("09:00", "17:00", DayOfWeek.SUNDAY), zone);
            case "BAY" -> rules(60, 60, 720, 0, hours("00:00", "24:00"), zone);
            case "TRAINER" -> rules(15, 45, 45, 15, hours("06:00", "22:00"), zone);
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static EffectiveRules rules(int slot, int min, int max, int buffer, WeeklyHours hours, ZoneId zone) {
        return new EffectiveRules(BookingModel.EXCLUSIVE, slot, min, max, buffer, 120, 3, 7, zone, hours, false);
    }

    private static LockedBookable bookable(EffectiveRules rules, boolean open) {
        BookableInfo info = new BookableInfo(1, "Test", "Test", "Test venue", "Testville", "IN", rules.zone(), rules.cancellationCutoffMinutes());
        return new LockedBookable(info, 1, "TEST", "TEST", open, rules);
    }

    /** The error code the rules return, or "OK". */
    private static String check(LockedBookable bookable, Instant start, Instant end, Instant now) {
        try {
            BookingRules.checkTimes(start, end);
            BookingRules.checkHold(bookable, start, end, now);
            return "OK";
        } catch (DomainException ex) {
            return ex.errorCode().code();
        }
    }

    @ParameterizedTest(name = "{0} {1} → {2}: {3}")
    @DisplayName("BR-02/03/04/07 per type default")
    @CsvSource({
            // Room: 30-min grid, 30 min to 3 h, 08:00-22:00
            "ROOM,    2026-11-10T10:00, 2026-11-10T10:30, OK",
            "ROOM,    2026-11-10T10:00, 2026-11-10T13:00, OK",
            "ROOM,    2026-11-10T10:00, 2026-11-10T13:30, DURATION_INVALID",
            "ROOM,    2026-11-10T10:15, 2026-11-10T10:45, OFF_GRID",
            "ROOM,    2026-11-10T10:00, 2026-11-10T10:45, OFF_GRID",
            "ROOM,    2026-11-10T07:30, 2026-11-10T08:30, OUTSIDE_OPERATING_HOURS",
            "ROOM,    2026-11-10T21:30, 2026-11-10T22:00, OK",
            "ROOM,    2026-11-10T21:30, 2026-11-10T22:30, OUTSIDE_OPERATING_HOURS",
            // Seat: 60-min grid, 1 to 4 h
            "SEAT,    2026-11-10T10:00, 2026-11-10T14:00, OK",
            "SEAT,    2026-11-10T10:00, 2026-11-10T15:00, DURATION_INVALID",
            // Doctor: exactly 30 min, closed on Sundays
            "DOCTOR,  2026-11-10T09:00, 2026-11-10T09:30, OK",
            "DOCTOR,  2026-11-10T09:00, 2026-11-10T10:00, DURATION_INVALID",
            "DOCTOR,  2026-11-15T10:00, 2026-11-15T10:30, OUTSIDE_OPERATING_HOURS",
            // Bay: up to 12 h, open 00:00-24:00; ending at midnight is fine, crossing it is not (A-06)
            "BAY,     2026-11-10T12:00, 2026-11-11T00:00, OK",
            "BAY,     2026-11-10T13:00, 2026-11-11T01:00, OUTSIDE_OPERATING_HOURS",
            "BAY,     2026-11-10T00:00, 2026-11-10T13:00, DURATION_INVALID",
            // Trainer: 15-min grid, exactly 45 min (the 15-min buffer is BR-10's business)
            "TRAINER, 2026-11-10T10:00, 2026-11-10T10:45, OK",
            "TRAINER, 2026-11-10T10:15, 2026-11-10T11:00, OK",
            "TRAINER, 2026-11-10T10:00, 2026-11-10T11:00, DURATION_INVALID",
            "TRAINER, 2026-11-10T10:05, 2026-11-10T10:50, OFF_GRID",
            // Horizon in whole local days (D-39): Sunday 15th is the last bookable day
            "ROOM,    2026-11-15T21:00, 2026-11-15T21:30, OK",
            "ROOM,    2026-11-16T08:00, 2026-11-16T08:30, BEYOND_HORIZON",
            // Already started
            "ROOM,    2026-11-09T07:30, 2026-11-09T08:30, START_IN_PAST",
    })
    void rulesPerType(String type, String start, String end, String expected) {
        EffectiveRules rules = rules(type, KOLKATA);
        assertThat(check(bookable(rules, true), at(start, KOLKATA), at(end, KOLKATA), NOW)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}: {1} → {2}")
    @DisplayName("BR-14: the first failing rule wins")
    @CsvSource({
            "inactive and in the past,             false, CAPACITY,  2026-11-09T07:00, 2026-11-09T07:30, BOOKABLE_INACTIVE",
            "capacity and in the past,             true,  CAPACITY,  2026-11-09T07:00, 2026-11-09T07:30, WRONG_BOOKING_MODEL",
            "beyond the horizon and before opening, true,  EXCLUSIVE, 2026-11-16T07:00, 2026-11-16T07:30, BEYOND_HORIZON",
            "before opening and off the grid,      true,  EXCLUSIVE, 2026-11-10T07:15, 2026-11-10T07:45, OUTSIDE_OPERATING_HOURS",
            "off the grid and too long,            true,  EXCLUSIVE, 2026-11-10T10:15, 2026-11-10T14:15, OFF_GRID",
            "end before start and inactive,        false, EXCLUSIVE, 2026-11-10T11:00, 2026-11-10T10:00, VALIDATION_FAILED",
    })
    void ruleOrder_BR14(String description, boolean open, BookingModel model, String start, String end, String expected) {
        EffectiveRules room = rules("ROOM", KOLKATA);
        EffectiveRules rules = new EffectiveRules(model, room.slotMinutes(), room.minDurationMinutes(),
                room.maxDurationMinutes(), 0, 120, 3, 7, KOLKATA, room.hours(), false);
        assertThat(check(bookable(rules, open), at(start, KOLKATA), at(end, KOLKATA), NOW)).isEqualTo(expected);
    }

    @Test
    @DisplayName("BR-03: seconds off the minute are off the grid")
    void secondsAreOffGrid_BR03() {
        Instant start = at("2026-11-10T10:00", KOLKATA).plusSeconds(30);
        assertThat(check(bookable(rules("ROOM", KOLKATA), true), start, start.plusSeconds(1800), NOW)).isEqualTo("OFF_GRID");
    }

    @Test
    @DisplayName("DST: on spring-forward night 00:00–02:00 London is one real hour; hours use the wall clock, length real time")
    void daylightSavingDay() {
        ZoneId london = ZoneId.of("Europe/London");
        EffectiveRules hourly = rules(60, 60, 60, 0, hours("00:00", "24:00"), london);
        Instant now = at("2026-03-27T12:00", london);
        // 29 March 2026: clocks go from 01:00 to 02:00, so 00:00 to 02:00 local lasts 60 minutes.
        assertThat(check(bookable(hourly, true), at("2026-03-29T00:00", london), at("2026-03-29T02:00", london), now))
                .isEqualTo("OK");
        assertThat(check(bookable(hourly, true), at("2026-03-29T02:00", london), at("2026-03-29T04:00", london), now))
                .isEqualTo("DURATION_INVALID");
    }

    @Test
    @DisplayName("BR-08: the fourth active booking in a category is refused")
    void memberLimit_BR08() {
        EffectiveRules room = rules("ROOM", KOLKATA);
        BookingRules.checkMemberLimit(2, room);
        try {
            BookingRules.checkMemberLimit(3, room);
            throw new AssertionError("expected MEMBER_LIMIT_REACHED");
        } catch (DomainException ex) {
            assertThat(ex.errorCode().code()).isEqualTo("MEMBER_LIMIT_REACHED");
            assertThat(ex.getMessage()).contains("at most 3");
        }
    }
}
