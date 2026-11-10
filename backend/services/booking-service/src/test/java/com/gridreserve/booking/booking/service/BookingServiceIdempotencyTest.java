package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.booking.dto.BookingResponse;
import com.gridreserve.booking.booking.dto.CreateHoldRequest;
import com.gridreserve.booking.booking.repository.BookingRepository;
import com.gridreserve.booking.catalog.service.CatalogService;
import com.gridreserve.booking.common.exception.BookingErrorCode;
import com.gridreserve.booking.member.service.MemberProvisioner;
import com.gridreserve.common.web.error.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IDEM-06, what happens to a claimed key when the hold fails: a business refusal (4xx) is stored so a retry
 * gets the same answer; anything else (a 5xx) gives the key back so the client may retry with it. The failures
 * are hard to cause through HTTP, so this tests the orchestration in {@link BookingService} with mocks.
 */
class BookingServiceIdempotencyTest {

    private static final CreateHoldRequest REQUEST = new CreateHoldRequest(1L,
            Instant.parse("2026-11-20T04:30:00Z"), Instant.parse("2026-11-20T05:30:00Z"));
    private static final String HASH = IdempotencyService.hash(REQUEST);

    RetriedTransactions transactions;
    IdempotencyService idempotency;
    BookingService service;

    @BeforeEach
    void setUp() {
        transactions = mock(RetriedTransactions.class);
        idempotency = mock(IdempotencyService.class);
        service = new BookingService(transactions, idempotency, mock(MemberProvisioner.class),
                mock(BookingRepository.class), mock(CatalogService.class));
        when(idempotency.claim(7L, "key-1", HASH)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("IDEM-06: an unexpected failure (5xx) releases the key and stores nothing")
    void unexpectedFailureReleasesKey_IDEM06() {
        when(transactions.hold(eq(7L), eq(REQUEST), any())).thenThrow(new IllegalStateException("database gone"));

        assertThatThrownBy(() -> service.hold(7L, REQUEST, "key-1")).isInstanceOf(IllegalStateException.class);

        verify(idempotency).release(7L, "key-1");
        verify(idempotency, never()).storeRefusal(any(Long.class), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("IDEM-06: a business refusal (4xx) is stored for replay and the key is kept")
    void refusalIsStored_IDEM06() {
        DomainException taken = new DomainException(BookingErrorCode.SLOT_TAKEN, "taken");
        when(transactions.hold(eq(7L), eq(REQUEST), any())).thenThrow(taken);

        assertThatThrownBy(() -> service.hold(7L, REQUEST, "key-1")).isSameAs(taken);

        verify(idempotency).storeRefusal(7L, "key-1", HASH, taken);
        verify(idempotency, never()).release(any(Long.class), anyString());
    }

    @Test
    @DisplayName("IDEM-01: without a key nothing is claimed or stored")
    void noKey_IDEM01() {
        BookingResponse created = mock(BookingResponse.class);
        when(transactions.hold(7L, REQUEST, null)).thenReturn(created);

        HoldResult result = service.hold(7L, REQUEST, null);

        assertThat(result.body()).isSameAs(created);
        assertThat(result.replayed()).isFalse();
        verify(idempotency, never()).claim(any(Long.class), anyString(), anyString());
    }
}
