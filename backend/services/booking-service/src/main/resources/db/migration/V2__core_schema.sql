-- Phase 1 booking_db schema (ARCHITECTURE.md §5.3). Every constraint is named: the service maps
-- database errors to API errors by constraint name, and tests assert on these names.
-- All moments are timestamptz (stored in UTC); opening hours are minutes from local midnight.

-- Local row per identity user (no personal data): the lock target for per-member limits (BR-08)
-- and the owner of bookings. Created on first use with INSERT ... ON CONFLICT DO NOTHING.
CREATE TABLE member (
    id          bigint      NOT NULL,           -- = identity user id (JWT sub)
    created_at  timestamptz NOT NULL,
    CONSTRAINT pk_member PRIMARY KEY (id)
);

CREATE TABLE category (
    id                              bigint      GENERATED ALWAYS AS IDENTITY,
    code                            varchar(30) NOT NULL,   -- STUDY, SPORTS, PARKING, CLINIC, GYM
    name                            varchar(60) NOT NULL,
    cancellation_cutoff_minutes     int         NOT NULL DEFAULT 120,
    max_active_bookings_per_member  int         NOT NULL DEFAULT 3,
    offer_ttl_minutes               int         NOT NULL DEFAULT 10,
    booking_horizon_days            int         NOT NULL DEFAULT 7,
    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT uq_category_code UNIQUE (code),
    CONSTRAINT ck_category_cutoff CHECK (cancellation_cutoff_minutes >= 0),
    CONSTRAINT ck_category_max_active CHECK (max_active_bookings_per_member >= 1),
    CONSTRAINT ck_category_offer_ttl CHECK (offer_ttl_minutes >= 1),
    CONSTRAINT ck_category_horizon CHECK (booking_horizon_days >= 1)
);

-- Labels, booking model and default rules per kind of bookable (Room, Seat, Court, ...).
CREATE TABLE bookable_type (
    id                    bigint      GENERATED ALWAYS AS IDENTITY,
    category_id           bigint      NOT NULL,
    code                  varchar(30) NOT NULL,     -- ROOM, SEAT, HALL, COURT, ...
    label                 varchar(30) NOT NULL,     -- UI label
    booking_model         varchar(20) NOT NULL,
    slot_minutes          int,                      -- null for CAPACITY types
    min_duration_minutes  int,
    max_duration_minutes  int,
    buffer_minutes        int         NOT NULL DEFAULT 0,
    CONSTRAINT pk_bookable_type PRIMARY KEY (id),
    CONSTRAINT fk_bookable_type_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT uq_bookable_type_code UNIQUE (category_id, code),
    CONSTRAINT ck_bookable_type_model CHECK (booking_model IN ('EXCLUSIVE', 'CAPACITY')),
    CONSTRAINT ck_bookable_type_slot CHECK (slot_minutes IN (15, 30, 60)),
    CONSTRAINT ck_bookable_type_buffer CHECK (buffer_minutes BETWEEN 0 AND 120),
    CONSTRAINT ck_bookable_type_durations CHECK (min_duration_minutes > 0 AND max_duration_minutes > 0),
    CONSTRAINT ck_bookable_type_exclusive_rules CHECK (
        booking_model = 'CAPACITY'
        OR (slot_minutes IS NOT NULL AND min_duration_minutes <= max_duration_minutes))
);

-- Valid (country, IANA zone) pairs, seeded from IANA zone.tab (FR-CAT-14, V3).
CREATE TABLE country_time_zone (
    country_code  char(2)     NOT NULL,     -- ISO 3166-1 alpha-2
    time_zone     varchar(64) NOT NULL,     -- IANA id, e.g. Asia/Kolkata
    CONSTRAINT pk_country_time_zone PRIMARY KEY (country_code, time_zone)
);

CREATE TABLE venue (
    id            bigint       GENERATED ALWAYS AS IDENTITY,
    category_id   bigint       NOT NULL,
    name          varchar(100) NOT NULL,
    address_line  varchar(200),
    country_code  char(2)      NOT NULL,
    time_zone     varchar(64)  NOT NULL,
    active        boolean      NOT NULL DEFAULT true,
    version       bigint       NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT pk_venue PRIMARY KEY (id),
    CONSTRAINT fk_venue_category FOREIGN KEY (category_id) REFERENCES category (id),
    -- The zone must belong to the venue's country: one composite FK instead of Java checks.
    CONSTRAINT fk_venue_country_zone FOREIGN KEY (country_code, time_zone)
        REFERENCES country_time_zone (country_code, time_zone)
);

-- Weekly opening hours (A-06): one interval per ISO weekday, no row = closed that day.
-- Minutes from local midnight (D-16a): opens 0..1439, closes 1..1440 (1440 = 24:00).
CREATE TABLE venue_opening_hours (
    venue_id          bigint   NOT NULL,
    iso_weekday       smallint NOT NULL,    -- 1 = Monday ... 7 = Sunday
    opens_at_minute   int      NOT NULL,
    closes_at_minute  int      NOT NULL,
    CONSTRAINT pk_venue_opening_hours PRIMARY KEY (venue_id, iso_weekday),
    CONSTRAINT fk_venue_opening_hours_venue FOREIGN KEY (venue_id) REFERENCES venue (id),
    CONSTRAINT ck_venue_opening_hours_weekday CHECK (iso_weekday BETWEEN 1 AND 7),
    CONSTRAINT ck_venue_opening_hours_opens CHECK (opens_at_minute BETWEEN 0 AND 1439),
    CONSTRAINT ck_venue_opening_hours_closes CHECK (closes_at_minute BETWEEN 1 AND 1440),
    CONSTRAINT ck_venue_opening_hours_order CHECK (opens_at_minute < closes_at_minute)
);

CREATE TABLE bookable (
    id                    bigint       GENERATED ALWAYS AS IDENTITY,
    venue_id              bigint       NOT NULL,
    bookable_type_id      bigint       NOT NULL,
    name                  varchar(100) NOT NULL,
    active                boolean      NOT NULL DEFAULT true,
    -- Optional overrides of the type's rules; null = inherit (FR-CAT-04/05). Same limits as the type.
    slot_minutes          int,
    min_duration_minutes  int,
    max_duration_minutes  int,
    buffer_minutes        int,
    version               bigint       NOT NULL DEFAULT 0,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    CONSTRAINT pk_bookable PRIMARY KEY (id),
    CONSTRAINT fk_bookable_venue FOREIGN KEY (venue_id) REFERENCES venue (id),
    CONSTRAINT fk_bookable_type FOREIGN KEY (bookable_type_id) REFERENCES bookable_type (id),
    CONSTRAINT ck_bookable_slot CHECK (slot_minutes IN (15, 30, 60)),
    CONSTRAINT ck_bookable_buffer CHECK (buffer_minutes BETWEEN 0 AND 120),
    CONSTRAINT ck_bookable_durations CHECK (min_duration_minutes > 0 AND max_duration_minutes > 0),
    CONSTRAINT ck_bookable_duration_order CHECK (min_duration_minutes <= max_duration_minutes)
);
-- "The type belongs to the venue's category" (FR-CAT-08) is checked in the service, not by a trigger.

-- Optional own weekly hours. If a bookable has any rows here, they replace the venue's hours (FR-CAT-04).
CREATE TABLE bookable_opening_hours (
    bookable_id       bigint   NOT NULL,
    iso_weekday       smallint NOT NULL,
    opens_at_minute   int      NOT NULL,
    closes_at_minute  int      NOT NULL,
    CONSTRAINT pk_bookable_opening_hours PRIMARY KEY (bookable_id, iso_weekday),
    CONSTRAINT fk_bookable_opening_hours_bookable FOREIGN KEY (bookable_id) REFERENCES bookable (id),
    CONSTRAINT ck_bookable_opening_hours_weekday CHECK (iso_weekday BETWEEN 1 AND 7),
    CONSTRAINT ck_bookable_opening_hours_opens CHECK (opens_at_minute BETWEEN 0 AND 1439),
    CONSTRAINT ck_bookable_opening_hours_closes CHECK (closes_at_minute BETWEEN 1 AND 1440),
    CONSTRAINT ck_bookable_opening_hours_order CHECK (opens_at_minute < closes_at_minute)
);

CREATE TABLE booking (
    id              bigint       GENERATED ALWAYS AS IDENTITY,
    member_id       bigint       NOT NULL,
    bookable_id     bigint       NOT NULL,
    session_id      bigint,                 -- CAPACITY seat bookings (Phase 2, FK added then); null = EXCLUSIVE
    start_time      timestamptz  NOT NULL,
    end_time        timestamptz  NOT NULL,
    -- end_time + buffer, copied at insert so a later buffer change never alters existing bookings.
    occupied_until  timestamptz  NOT NULL,
    status          varchar(20)  NOT NULL,
    expires_at      timestamptz,            -- set only while HELD
    cancelled_at    timestamptz,
    cancelled_by    varchar(10),
    cancel_reason   varchar(200),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_booking PRIMARY KEY (id),
    CONSTRAINT fk_booking_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_booking_bookable FOREIGN KEY (bookable_id) REFERENCES bookable (id),
    CONSTRAINT ck_booking_status CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_booking_cancelled_by CHECK (cancelled_by IN ('MEMBER', 'ADMIN', 'SYSTEM')),
    CONSTRAINT ck_booking_time_order CHECK (start_time < end_time),
    CONSTRAINT ck_booking_occupied_until CHECK (occupied_until >= end_time),
    CONSTRAINT ck_booking_held_expires CHECK ((status = 'HELD') = (expires_at IS NOT NULL)),

    -- BR-10: at most one active booking per bookable at any instant, buffer included.
    -- '[)' = start included, end excluded, so back-to-back bookings do not overlap (BR-12).
    CONSTRAINT ex_booking_bookable_overlap EXCLUDE USING gist (
        bookable_id WITH =,
        tstzrange(start_time, occupied_until, '[)') WITH &&
    ) WHERE (status IN ('HELD', 'CONFIRMED') AND session_id IS NULL),

    -- BR-11: a member never has two active bookings at the same time, in any category.
    -- Uses end_time (no buffer): the buffer belongs to the bookable, not to the member.
    CONSTRAINT ex_booking_member_overlap EXCLUDE USING gist (
        member_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status IN ('HELD', 'CONFIRMED'))
);

CREATE INDEX ix_booking_member_active ON booking (member_id, end_time) WHERE status IN ('HELD', 'CONFIRMED');
CREATE INDEX ix_booking_held_expiry   ON booking (expires_at)          WHERE status = 'HELD';
CREATE INDEX ix_booking_bookable_time ON booking (bookable_id, start_time);
