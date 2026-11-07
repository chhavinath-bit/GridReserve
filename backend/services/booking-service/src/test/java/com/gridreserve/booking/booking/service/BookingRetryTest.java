package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.booking.dto.BookingResponse;
import com.gridreserve.booking.booking.dto.CreateHoldRequest;
import com.gridreserve.booking.booking.repository.BookingRepository;
import com.gridreserve.booking.catalog.service.CatalogService;
import com.gridreserve.booking.common.exception.BookingErrorCode;
import com.gridreserve.booking.config.RetryConfig;
import com.gridreserve.booking.member.service.MemberProvisioner;
import com.gridreserve.common.web.error.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-11: {@link BookingService} reruns a transaction PostgreSQL killed to break a deadlock, and nothing
 * else. A small Spring context (no database): the real {@link RetryConfig} and service, with the
 * transaction class mocked so it can fail on demand. Without Spring there would be no retry proxy.
 */
@SpringJUnitConfig
@Import({RetryConfig.class, BookingService.class})
class BookingRetryTest {

    private static final CreateHoldRequest REQUEST = new CreateHoldRequest(1L,
            Instant.parse("2026-11-08T04:30:00Z"), Instant.parse("2026-11-08T05:30:00Z"));

    @MockitoBean
    BookingTransactions transactions;

    @MockitoBean
    MemberProvisioner members;

    @MockitoBean
    BookingRepository bookings;

    @MockitoBean
    CatalogService catalog;

    @Autowired
    BookingService service;

    // How Spring reports PostgreSQL's 40P01 (deadlock detected) through JPA.
    private static CannotAcquireLockException deadlock() {
        return new CannotAcquireLockException("deadlock detected");
    }

    @Test
    @DisplayName("D-11: a deadlocked hold is run again in a new transaction and succeeds")
    void retriesAfterDeadlock() {
        BookingResponse created = mock(BookingResponse.class);
        when(transactions.hold(anyLong(), eq(REQUEST))).thenThrow(deadlock()).thenReturn(created);

        assertThat(service.hold(7L, REQUEST)).isSameAs(created);
        verify(transactions, times(2)).hold(7L, REQUEST);
    }

    @Test
    @DisplayName("D-11: three attempts at most, then the failure is reported")
    void givesUpAfterThreeAttempts() {
        when(transactions.confirm(7L, 5L)).thenThrow(deadlock());

        assertThatThrownBy(() -> service.confirm(7L, 5L)).isInstanceOf(CannotAcquireLockException.class);
        verify(transactions, times(3)).confirm(7L, 5L);
    }

    @Test
    @DisplayName("D-11: a business refusal (409 SLOT_TAKEN) is never retried")
    void businessRefusalNotRetried() {
        when(transactions.hold(anyLong(), eq(REQUEST)))
                .thenThrow(new DomainException(BookingErrorCode.SLOT_TAKEN, "taken"));

        assertThatThrownBy(() -> service.hold(7L, REQUEST)).isInstanceOf(DomainException.class);
        verify(transactions, times(1)).hold(7L, REQUEST);
    }
}
