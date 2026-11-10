-- M1.12: the booking audit log (FR-AUD-01..03) and idempotency records for hold creation (IDEM-01..07).

-- One row per booking state change, written in the same transaction as the change (FR-AUD-02). Rows are
-- only ever added. The foreign key has no ON DELETE CASCADE on purpose: a booking with history can't be
-- deleted without first removing its history, and the application deletes neither.
CREATE TABLE booking_audit (
    id             bigint       GENERATED ALWAYS AS IDENTITY,
    booking_id     bigint       NOT NULL,
    from_status    varchar(20),                -- null for the creation of a booking
    to_status      varchar(20)  NOT NULL,
    actor_type     varchar(10)  NOT NULL,
    actor_user_id  bigint,                     -- identity user ID (no FK across services); null for SYSTEM
    reason         varchar(200),
    occurred_at    timestamptz  NOT NULL,
    CONSTRAINT pk_booking_audit PRIMARY KEY (id),
    CONSTRAINT fk_booking_audit_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT ck_booking_audit_actor CHECK (actor_type IN ('MEMBER', 'ADMIN', 'SYSTEM')),
    CONSTRAINT ck_booking_audit_system_actor CHECK ((actor_type = 'SYSTEM') = (actor_user_id IS NULL)),
    CONSTRAINT ck_booking_audit_from_status CHECK (from_status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_booking_audit_to_status CHECK (to_status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX ix_booking_audit_booking ON booking_audit (booking_id, occurred_at, id);

-- One row per (member, Idempotency-Key) on POST /bookings. IN_PROGRESS carries a lease (IDEM-05); a completed
-- request keeps its answer for 24 hours (IDEM-07) so a retry gets the same answer (IDEM-03).
CREATE TABLE idempotency_record (
    member_id        bigint       NOT NULL,
    idem_key         varchar(64)  NOT NULL,
    request_hash     char(64)     NOT NULL,      -- SHA-256 (hex) of the normalized request
    state            varchar(12)  NOT NULL,
    response_status  int,
    response_body    jsonb,
    booking_id       bigint,
    created_at       timestamptz  NOT NULL,
    locked_until     timestamptz  NOT NULL,
    expires_at       timestamptz  NOT NULL,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (member_id, idem_key),
    CONSTRAINT fk_idempotency_record_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_idempotency_record_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT ck_idempotency_record_state CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_idempotency_record_completed CHECK (
        (state = 'IN_PROGRESS' AND response_status IS NULL AND response_body IS NULL)
        OR (state = 'COMPLETED' AND response_status IS NOT NULL AND response_body IS NOT NULL))
);

CREATE INDEX ix_idempotency_record_expiry ON idempotency_record (expires_at);
