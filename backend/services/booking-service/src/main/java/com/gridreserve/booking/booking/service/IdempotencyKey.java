package com.gridreserve.booking.booking.service;

/** A claimed key, passed into the hold's transaction so it can store its answer there. */
record IdempotencyKey(long memberId, String key, String requestHash) {
}
