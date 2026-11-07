package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.catalog.dto.LockedBookable;
import com.gridreserve.booking.catalog.entity.BookingModel;
import com.gridreserve.booking.common.exception.BookingErrorCode;
import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.DomainException;

import java.time.Instant;

/**
 * The booking rules (REQUIREMENTS §6), checked in the BR-14 order so the first failure is always the
 * same one. M1.5 has BR-01, 06, 09 and 05; M1.7 adds 07, 02, 03, 04 and 08 in their places. The
 * values come from the catalog's resolved rules, never from a category or type code.
 *
 * <p>These checks are friendly early answers. The database still has the last word on overlaps
 * (BR-10, BR-11), because two requests can both pass these checks at the same moment.
 */
final class BookingRules {

    private BookingRules() {
    }

    /** BR-01: needs no data, so it runs before any lock is taken. */
    static void checkTimes(Instant start, Instant end) {
        if (!start.isBefore(end)) {
            throw new DomainException(CommonErrorCode.VALIDATION_FAILED, "The end must be after the start.");
        }
    }

    /** BR-06, BR-09, BR-05, on a bookable read under lock. */
    static void checkHold(LockedBookable bookable, Instant start, Instant now) {
        if (!bookable.open()) {
            throw new DomainException(BookingErrorCode.BOOKABLE_INACTIVE, "This can't be booked at the moment.");
        }
        if (bookable.rules().bookingModel() != BookingModel.EXCLUSIVE) {
            throw new DomainException(BookingErrorCode.WRONG_BOOKING_MODEL, "This can't be booked by time slot yet.");
        }
        if (!start.isAfter(now)) {
            throw new DomainException(BookingErrorCode.START_IN_PAST, "This time has already started.");
        }
    }
}
