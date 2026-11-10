package com.gridreserve.booking.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Removes test bookings together with their audit rows and idempotency records (M1.12). The foreign keys
 * from those tables have no ON DELETE CASCADE on purpose (a booking's history can't vanish with it), so a
 * test deletes them first. {@code where} is a condition on {@code booking}, e.g. {@code "member_id = ?"}.
 */
public final class TestBookings {

    private TestBookings() {
    }

    public static void delete(JdbcTemplate jdbc, String where, Object... args) {
        String ids = "SELECT id FROM booking WHERE " + where;
        jdbc.update("DELETE FROM idempotency_record WHERE booking_id IN (" + ids + ")", args);
        jdbc.update("DELETE FROM booking_audit WHERE booking_id IN (" + ids + ")", args);
        jdbc.update("DELETE FROM booking WHERE " + where, args);
    }
}
