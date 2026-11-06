package com.gridreserve.booking.common.web;

import com.gridreserve.common.web.error.CommonErrorCode;
import com.gridreserve.common.web.error.DomainException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Checks {@code page} and {@code size} query parameters against API-04: page from 0, size 1 to 100. */
public final class PageParams {

    public static final String DEFAULT_SIZE = "20";
    private static final int MAX_SIZE = 100;

    private PageParams() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new DomainException(CommonErrorCode.VALIDATION_FAILED,
                    "page must be 0 or more and size between 1 and " + MAX_SIZE + ".");
        }
        return PageRequest.of(page, size, sort);
    }
}
