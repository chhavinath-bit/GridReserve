package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.catalog.dto.LockedBookable;
import com.gridreserve.booking.catalog.entity.BookingModel;
import com.gridreserve.booking.catalog.service.EffectiveRules;
import com.gridreserve.booking.catalog.service.OpeningInterval;
import com.gridreserve.booking.common.exception.BookingErrorCode;
import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * The booking rules (REQUIREMENTS §6), checked in the BR-14 order so the first failure is always the
 * same one: BR-01, BR-06, BR-09, BR-05, BR-07, BR-02, BR-03, BR-04, BR-08, then the insert (BR-10,
 * BR-11). Every value comes from the catalog's resolved rules, never from a category or type code.
 *
 * <p>Opening hours and the slot grid are wall-clock rules, read in the venue's zone; durations are
 * real elapsed time. On a daylight-saving day the two can differ, and each rule uses the right one.
 *
 * <p>These checks are friendly early answers. The database still has the last word on overlaps
 * (BR-10, BR-11), because two requests can both pass these checks at the same moment.
 */
final class BookingRules {

    private static final int MINUTES_PER_DAY = 24 * 60;

    private BookingRules() {
    }

    /** BR-01: needs no data, so it runs before any lock is taken. */
    static void checkTimes(Instant start, Instant end) {
        if (!start.isBefore(end)) {
            throw new DomainException(CommonErrorCode.VALIDATION_FAILED, "The end must be after the start.");
        }
    }

    /** BR-06, BR-09, BR-05, BR-07, BR-02, BR-03, BR-04, on a bookable read under lock. */
    static void checkHold(LockedBookable bookable, Instant start, Instant end, Instant now) {
        EffectiveRules rules = bookable.rules();
        if (!bookable.open()) {
            throw refuse(BookingErrorCode.BOOKABLE_INACTIVE, "This can't be booked at the moment.");
        }
        if (rules.bookingModel() != BookingModel.EXCLUSIVE) {
            throw refuse(BookingErrorCode.WRONG_BOOKING_MODEL, "This can't be booked by time slot yet.");
        }
        if (!start.isAfter(now)) {
            throw refuse(BookingErrorCode.START_IN_PAST, "This time has already started.");
        }

        ZonedDateTime localStart = start.atZone(rules.zone());
        ZonedDateTime localEnd = end.atZone(rules.zone());
        LocalDate day = localStart.toLocalDate();
        if (!day.isBefore(rules.firstDateBeyondHorizon(now))) {
            throw refuse(BookingErrorCode.BEYOND_HORIZON,
                    "Bookings open " + rules.bookingHorizonDays() + " days ahead.");
        }

        OpeningInterval hours = rules.hours().on(day.getDayOfWeek())
                .orElseThrow(() -> refuse(BookingErrorCode.OUTSIDE_OPERATING_HOURS, "Closed on this day."));
        int startMinute = minuteOfDay(localStart);
        int endMinute = endMinuteOnDay(localEnd, day)
                .orElseThrow(() -> refuse(BookingErrorCode.OUTSIDE_OPERATING_HOURS,
                        "A booking must end on the day it starts."));
        if (startMinute < hours.opensAtMinute() || endMinute > hours.closesAtMinute()) {
            throw refuse(BookingErrorCode.OUTSIDE_OPERATING_HOURS, "Open " + OpeningInterval.format(hours.opensAtMinute())
                    + "–" + OpeningInterval.format(hours.closesAtMinute()) + " on this day.");
        }

        int slot = rules.slotMinutes();
        Duration length = Duration.between(start, end);
        boolean wholeMinutes = startsOnMinute(localStart) && length.toSecondsPart() == 0 && length.toNanosPart() == 0;
        long minutes = length.toMinutes();
        if (!wholeMinutes || (startMinute - hours.opensAtMinute()) % slot != 0 || minutes % slot != 0) {
            throw refuse(BookingErrorCode.OFF_GRID, "Pick start and end times from the " + slot + "-minute slots.");
        }

        if (minutes < rules.minDurationMinutes() || minutes > rules.maxDurationMinutes()) {
            throw refuse(BookingErrorCode.DURATION_INVALID, rules.minDurationMinutes().equals(rules.maxDurationMinutes())
                    ? "A booking here lasts exactly " + rules.minDurationMinutes() + " minutes."
                    : "A booking here lasts " + rules.minDurationMinutes() + " to " + rules.maxDurationMinutes() + " minutes.");
        }
    }

    /**
     * BR-08: the member's active bookings in this category that have not ended, before this one. The
     * caller counts them while holding the member's row lock, so two parallel holds can't both see 2.
     */
    static void checkMemberLimit(long activeInCategory, EffectiveRules rules) {
        if (activeInCategory >= rules.maxActiveBookingsPerMember()) {
            throw refuse(BookingErrorCode.MEMBER_LIMIT_REACHED, "You can have at most "
                    + rules.maxActiveBookingsPerMember() + " active bookings in this category.");
        }
    }

    private static int minuteOfDay(ZonedDateTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    /**
     * Where the booking ends, in minutes from the start day's midnight. Ending exactly at the next
     * midnight counts as 24:00 (a venue may close at 24:00); any later end crosses midnight (A-06).
     */
    private static Optional<Integer> endMinuteOnDay(ZonedDateTime localEnd, LocalDate day) {
        if (localEnd.toLocalDate().equals(day)) {
            return Optional.of(minuteOfDay(localEnd));
        }
        boolean nextMidnight = localEnd.toLocalDate().equals(day.plusDays(1)) && minuteOfDay(localEnd) == 0
                && startsOnMinute(localEnd);
        return nextMidnight ? Optional.of(MINUTES_PER_DAY) : Optional.empty();
    }

    private static boolean startsOnMinute(ZonedDateTime time) {
        return time.getSecond() == 0 && time.getNano() == 0;
    }

    private static DomainException refuse(BookingErrorCode code, String message) {
        return new DomainException(code, message);
    }
}
