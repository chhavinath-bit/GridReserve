package com.gridreserve.booking.availability;

import com.gridreserve.booking.availability.service.LocalSlot;
import com.gridreserve.booking.availability.service.LocalSlotGrid;
import com.gridreserve.booking.catalog.service.OpeningInterval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSlotGridTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    @ParameterizedTest(name = "{0}-minute slots from 08:00 to 22:00 → {1} slots")
    @CsvSource({"15, 56", "30, 28", "60, 14"})
    @DisplayName("BR-03: slots step by the slot length from opening to closing")
    void gridSizes(int slotMinutes, int expected) {
        List<LocalSlot> slots = LocalSlotGrid.slots(DAY, new OpeningInterval(480, 1320), slotMinutes, KOLKATA);

        assertThat(slots).hasSize(expected);
        assertThat(slots.getFirst().localStart()).isEqualTo(DAY.atTime(8, 0));
        assertThat(slots.getLast().localEnd()).isEqualTo(DAY.atTime(22, 0));
        assertThat(slots).allSatisfy(slot ->
                assertThat(Duration.between(slot.start(), slot.end())).isEqualTo(Duration.ofMinutes(slotMinutes)));
    }

    @Test
    @DisplayName("BR-03: the grid starts at that day's opening time; a remainder before closing is no slot")
    void anchoredAtOpening() {
        // 09:15 to 10:50 with 30-minute slots: 09:15, 09:45, 10:15; 10:45-11:15 would pass closing.
        List<LocalSlot> slots = LocalSlotGrid.slots(DAY, new OpeningInterval(555, 650), 30, KOLKATA);

        assertThat(slots).extracting(LocalSlot::localStart)
                .containsExactly(DAY.atTime(9, 15), DAY.atTime(9, 45), DAY.atTime(10, 15));
    }

    @Test
    @DisplayName("API-03: local times are venue time; instants are the matching UTC moments")
    void localAndUtc() {
        LocalSlot first = LocalSlotGrid.slots(DAY, new OpeningInterval(480, 1320), 60, KOLKATA).getFirst();

        assertThat(first.localStart()).isEqualTo(LocalDateTime.of(2026, 11, 9, 8, 0));
        assertThat(first.start()).isEqualTo(Instant.parse("2026-11-09T02:30:00Z")); // IST = UTC+05:30
    }

    @Test
    @DisplayName("Open all day (0 to 24:00): the last slot ends at the next midnight")
    void allDay() {
        List<LocalSlot> slots = LocalSlotGrid.slots(DAY, new OpeningInterval(0, 1440), 60, KOLKATA);

        assertThat(slots).hasSize(24);
        assertThat(slots.getLast().localEnd()).isEqualTo(DAY.plusDays(1).atStartOfDay());
    }

    @Test
    @DisplayName("DST spring forward (London, 29 Mar 2026): no slot at the hour that does not exist")
    void springForward() {
        LocalDate day = LocalDate.of(2026, 3, 29); // 01:00 jumps to 02:00
        List<LocalSlot> slots = LocalSlotGrid.slots(day, new OpeningInterval(0, 1440), 60, LONDON);

        assertThat(slots).hasSize(23);
        assertThat(slots).extracting(LocalSlot::localStart).doesNotContain(day.atTime(1, 0));
        assertThat(slots).allSatisfy(slot -> assertThat(slot.end()).isAfter(slot.start()));
    }

    @Test
    @DisplayName("DST fall back (London, 25 Oct 2026): one slot per local start, no crash")
    void fallBack() {
        LocalDate day = LocalDate.of(2026, 10, 25); // 01:00-02:00 happens twice
        List<LocalSlot> slots = LocalSlotGrid.slots(day, new OpeningInterval(0, 1440), 60, LONDON);

        assertThat(slots).hasSize(24);
        assertThat(slots).extracting(LocalSlot::localStart).doesNotHaveDuplicates();
        assertThat(slots).allSatisfy(slot -> assertThat(slot.end()).isAfter(slot.start()));
    }
}
