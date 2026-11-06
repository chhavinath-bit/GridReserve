package com.gridreserve.booking.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A kind of bookable within a category (Room, Seat, Court, ...): its UI label, booking model and
 * default rules. Category differences live here as data, never as code paths.
 */
@Entity
@Table(name = "bookable_type")
public class BookableType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 30)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_model", nullable = false, length = 20)
    private BookingModel bookingModel;

    // Null for CAPACITY types.
    @Column(name = "slot_minutes")
    private Integer slotMinutes;

    @Column(name = "min_duration_minutes")
    private Integer minDurationMinutes;

    @Column(name = "max_duration_minutes")
    private Integer maxDurationMinutes;

    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes;

    protected BookableType() {
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public BookingModel getBookingModel() {
        return bookingModel;
    }

    public Integer getSlotMinutes() {
        return slotMinutes;
    }

    public Integer getMinDurationMinutes() {
        return minDurationMinutes;
    }

    public Integer getMaxDurationMinutes() {
        return maxDurationMinutes;
    }

    public int getBufferMinutes() {
        return bufferMinutes;
    }
}
