package com.gridreserve.booking.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A category (Study, Sports, ...) with the rules shared by its venues. Seed data, read-only in Phase 1. */
@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "cancellation_cutoff_minutes", nullable = false)
    private int cancellationCutoffMinutes;

    @Column(name = "max_active_bookings_per_member", nullable = false)
    private int maxActiveBookingsPerMember;

    @Column(name = "offer_ttl_minutes", nullable = false)
    private int offerTtlMinutes;

    @Column(name = "booking_horizon_days", nullable = false)
    private int bookingHorizonDays;

    protected Category() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getCancellationCutoffMinutes() {
        return cancellationCutoffMinutes;
    }

    public int getMaxActiveBookingsPerMember() {
        return maxActiveBookingsPerMember;
    }

    public int getOfferTtlMinutes() {
        return offerTtlMinutes;
    }

    public int getBookingHorizonDays() {
        return bookingHorizonDays;
    }
}
