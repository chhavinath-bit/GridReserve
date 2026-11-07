package com.gridreserve.booking.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The service's clock in integration tests: real time plus an offset a test can move forward, e.g. to
 * let a hold run out without waiting 5 minutes. With no offset it is the real clock, so tests that
 * never move it behave exactly as before. Tests that move it reset it afterwards.
 */
public final class MovableClock extends Clock {

    private volatile Duration offset = Duration.ZERO;

    public void advance(Duration duration) {
        offset = offset.plus(duration);
    }

    public void reset() {
        offset = Duration.ZERO;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("The service clock is always UTC");
    }
}
