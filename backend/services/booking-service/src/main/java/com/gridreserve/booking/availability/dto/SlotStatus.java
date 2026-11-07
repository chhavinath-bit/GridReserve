package com.gridreserve.booking.availability.dto;

/** FR-AV-02. PAST wins over TAKEN: a slot that has started can't be booked, whoever has it. */
public enum SlotStatus {
    FREE,
    TAKEN,
    PAST
}
