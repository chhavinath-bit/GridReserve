package com.gridreserve.booking.booking.service;

import com.gridreserve.booking.booking.dto.CreateHoldRequest;
import com.gridreserve.booking.booking.entity.IdempotencyRecord;
import com.gridreserve.booking.booking.entity.IdempotencyRecordId;
import com.gridreserve.booking.booking.repository.IdempotencyRecordRepository;
import com.gridreserve.booking.common.exception.BookingErrorCode;
import com.gridreserve.booking.common.exception.ErrorCodes;
import com.gridreserve.common.web.error.DomainException;
import com.gridreserve.common.web.error.ErrorCode;
import com.gridreserve.common.web.error.ErrorResponse.FieldError;
import com.gridreserve.common.web.error.FieldValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@code Idempotency-Key} on hold creation (IDEM-01..07, ARCHITECTURE §15.3), in three steps around the hold:
 * <ol>
 *   <li>{@link #claim}, its own short transaction: the first request with a key inserts it IN_PROGRESS with a
 *       30-second lease; a repeat gets the stored answer, {@code 409} while the first one runs, or {@code 422}
 *       if it is a different request;</li>
 *   <li>{@link #complete}, inside the hold's transaction: the new booking and its stored answer commit
 *       together;</li>
 *   <li>on failure: a business refusal (4xx) is stored and replayed later ({@link #storeRefusal}); anything
 *       else (5xx) gives the key back ({@link #release}), so the client may retry with it (IDEM-06).</li>
 * </ol>
 */
@Service
public class IdempotencyService {

    static final Duration LEASE = Duration.ofSeconds(30);
    static final Duration KEPT_FOR = Duration.ofHours(24);
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    public static final String HEADER = "Idempotency-Key";

    /** A stored answer: the status and body of the first request, and its booking if it made one. */
    public record StoredResponse(int status, String body, Long bookingId) {
    }

    private final IdempotencyRecordRepository records;
    private final JsonMapper json;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository records, JsonMapper json, Clock clock) {
        this.records = records;
        this.json = json;
        this.clock = clock;
    }

    /** IDEM-01: 1-64 characters from A-Z, a-z, 0-9, "_" and "-". */
    public static void checkFormat(String key) {
        if (!KEY.matcher(key).matches()) {
            throw new FieldValidationException(List.of(new FieldError(HEADER,
                    "Must be 1 to 64 characters: letters, digits, \"_\" or \"-\".")));
        }
    }

    /**
     * IDEM-03 "same request": the same bookable and the same times. Hashing these normalized values instead of
     * the raw JSON means a retry with other spacing or field order still counts as the same request.
     */
    public static String hash(CreateHoldRequest request) {
        String canonical = request.bookableId() + "|" + request.start() + "|" + request.end();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is part of every JDK", ex);
        }
    }

    /**
     * Step 1. Empty: this request owns the key now and should go on. Present: the stored answer to replay.
     * Throws {@code 409 IDEMPOTENCY_IN_PROGRESS} or {@code 422 IDEMPOTENCY_KEY_REUSED}.
     */
    @Transactional
    public Optional<StoredResponse> claim(long memberId, String key, String hash) {
        Instant now = now();
        // A first request and a release by a failing one can interleave; a few rounds settle it.
        for (int round = 0; round < 3; round++) {
            if (records.insertIfAbsent(memberId, key, hash, now, now.plus(LEASE), now.plus(KEPT_FOR)) == 1
                    || records.reclaimExpired(memberId, key, hash, now, now.plus(LEASE), now.plus(KEPT_FOR)) == 1) {
                return Optional.empty();
            }
            Optional<IdempotencyRecord> found = records.findById(new IdempotencyRecordId(memberId, key));
            if (found.isEmpty()) {
                continue;
            }
            IdempotencyRecord record = found.get();
            if (!record.getRequestHash().equals(hash)) {
                throw new DomainException(BookingErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "This Idempotency-Key was already used for a different booking request.");
            }
            if (record.isCompleted()) {
                return Optional.of(new StoredResponse(record.getResponseStatus(), record.getResponseBody(),
                        record.getBookingId()));
            }
            if (records.takeOver(memberId, key, hash, now, now.plus(LEASE)) == 1) {
                return Optional.empty();
            }
            throw new DomainException(BookingErrorCode.IDEMPOTENCY_IN_PROGRESS,
                    "This booking request is still being processed. Try again in a moment.");
        }
        throw new DomainException(BookingErrorCode.IDEMPOTENCY_IN_PROGRESS,
                "This booking request is still being processed. Try again in a moment.");
    }

    /** Step 2, in the hold's transaction. Fails (and so rolls the hold back) if the key is no longer ours. */
    @Transactional(propagation = Propagation.MANDATORY)
    void complete(IdempotencyKey key, int status, Object body, long bookingId) {
        if (records.complete(key.memberId(), key.key(), key.requestHash(), status, json.writeValueAsString(body),
                bookingId) != 1) {
            throw new IllegalStateException("The idempotency record changed while the hold was being created");
        }
    }

    /** Step 3 for a business refusal: stored, so the same request with the same key gets the same answer. */
    @Transactional
    public void storeRefusal(long memberId, String key, String hash, DomainException refusal) {
        String body = json.writeValueAsString(Map.of("code", refusal.errorCode().code(), "message", refusal.getMessage()));
        records.complete(memberId, key, hash, refusal.errorCode().status().value(), body, null);
    }

    /** Step 3 for anything unexpected: the key is free again. */
    @Transactional
    public void release(long memberId, String key) {
        records.release(memberId, key);
    }

    /** Turns a stored refusal back into the same error (the error handler writes the standard body). */
    public DomainException refusalOf(StoredResponse stored) {
        JsonNode body = json.readTree(stored.body());
        ErrorCode code = ErrorCodes.byName(body.get("code").asString())
                .orElseThrow(() -> new IllegalStateException("Unknown stored error code"));
        return new DomainException(code, body.get("message").asString());
    }

    public JsonNode bodyOf(StoredResponse stored) {
        return json.readTree(stored.body());
    }

    /** For the cleanup job (IDEM-07). */
    @Transactional
    public int deleteExpired() {
        return records.deleteExpired(now());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
