-- Demo catalog: 5 categories, their bookable types with the decided defaults (CLAUDE.md §15.1),
-- and one fictional venue per category. All names and addresses are invented.
-- Category differences are data here, never code paths.

INSERT INTO category (code, name, cancellation_cutoff_minutes, max_active_bookings_per_member, offer_ttl_minutes, booking_horizon_days)
VALUES ('STUDY',   'Study',   120, 3, 10, 7),
       ('SPORTS',  'Sports',  120, 3, 10, 7),
       ('PARKING', 'Parking', 120, 3, 10, 7),
       ('CLINIC',  'Clinic',  120, 3, 10, 7),
       ('GYM',     'Gym',     120, 3, 10, 7);

-- slot / min / max / buffer in minutes. HALL and CLASS are CAPACITY types, bookable from Phase 2.
INSERT INTO bookable_type (category_id, code, label, booking_model, slot_minutes, min_duration_minutes, max_duration_minutes, buffer_minutes)
SELECT c.id, t.code, t.label, t.booking_model, t.slot_minutes, t.min_duration, t.max_duration, t.buffer
FROM (VALUES
        ('STUDY',   'ROOM',    'Room',    'EXCLUSIVE', 30,   30,   180,  0),
        ('STUDY',   'SEAT',    'Seat',    'EXCLUSIVE', 60,   60,   240,  0),
        ('STUDY',   'HALL',    'Hall',    'CAPACITY',  NULL, NULL, NULL, 0),
        ('SPORTS',  'COURT',   'Court',   'EXCLUSIVE', 60,   60,   120,  0),
        ('PARKING', 'BAY',     'Bay',     'EXCLUSIVE', 60,   60,   720,  0),
        ('CLINIC',  'DOCTOR',  'Doctor',  'EXCLUSIVE', 30,   30,   30,   0),
        ('GYM',     'TRAINER', 'Trainer', 'EXCLUSIVE', 15,   45,   45,   15),
        ('GYM',     'CLASS',   'Class',   'CAPACITY',  NULL, NULL, NULL, 0)
     ) AS t (category_code, code, label, booking_model, slot_minutes, min_duration, max_duration, buffer)
JOIN category c ON c.code = t.category_code;

-- IronWorks is in London on purpose: a second zone, with daylight saving, next to the Indian venues.
INSERT INTO venue (category_id, name, address_line, country_code, time_zone, created_at, updated_at)
SELECT c.id, v.name, v.address_line, v.country_code, v.time_zone, now(), now()
FROM (VALUES
        ('STUDY',   'Central Library',  '12 Lake View Road, Pune',       'IN', 'Asia/Kolkata'),
        ('SPORTS',  'Sunrise Turf',     '4 Stadium Lane, Bengaluru',     'IN', 'Asia/Kolkata'),
        ('PARKING', 'Palm Apartments',  '88 Palm Grove, Chennai',        'IN', 'Asia/Kolkata'),
        ('CLINIC',  'CityCare Clinic',  '21 Hospital Street, Hyderabad', 'IN', 'Asia/Kolkata'),
        ('GYM',     'IronWorks',        '7 Foundry Walk, London',        'GB', 'Europe/London')
     ) AS v (category_code, name, address_line, country_code, time_zone)
JOIN category c ON c.code = v.category_code;

-- Weekly hours as minutes from local midnight (480 = 08:00, 1440 = 24:00). No row = closed.
INSERT INTO venue_opening_hours (venue_id, iso_weekday, opens_at_minute, closes_at_minute)
SELECT v.id, h.iso_weekday, h.opens, h.closes
FROM (VALUES
        -- Central Library: Mon-Sat 08:00-22:00, Sun 10:00-18:00
        ('Central Library', 1, 480, 1320), ('Central Library', 2, 480, 1320), ('Central Library', 3, 480, 1320),
        ('Central Library', 4, 480, 1320), ('Central Library', 5, 480, 1320), ('Central Library', 6, 480, 1320),
        ('Central Library', 7, 600, 1080),
        -- Sunrise Turf: daily 06:00-23:00
        ('Sunrise Turf', 1, 360, 1380), ('Sunrise Turf', 2, 360, 1380), ('Sunrise Turf', 3, 360, 1380),
        ('Sunrise Turf', 4, 360, 1380), ('Sunrise Turf', 5, 360, 1380), ('Sunrise Turf', 6, 360, 1380),
        ('Sunrise Turf', 7, 360, 1380),
        -- Palm Apartments: daily, all day
        ('Palm Apartments', 1, 0, 1440), ('Palm Apartments', 2, 0, 1440), ('Palm Apartments', 3, 0, 1440),
        ('Palm Apartments', 4, 0, 1440), ('Palm Apartments', 5, 0, 1440), ('Palm Apartments', 6, 0, 1440),
        ('Palm Apartments', 7, 0, 1440),
        -- CityCare Clinic: Mon-Fri 09:00-17:00, Sat 09:00-13:00, closed Sunday
        ('CityCare Clinic', 1, 540, 1020), ('CityCare Clinic', 2, 540, 1020), ('CityCare Clinic', 3, 540, 1020),
        ('CityCare Clinic', 4, 540, 1020), ('CityCare Clinic', 5, 540, 1020), ('CityCare Clinic', 6, 540, 780),
        -- IronWorks: Mon-Fri 06:00-22:00, Sat-Sun 08:00-20:00
        ('IronWorks', 1, 360, 1320), ('IronWorks', 2, 360, 1320), ('IronWorks', 3, 360, 1320),
        ('IronWorks', 4, 360, 1320), ('IronWorks', 5, 360, 1320), ('IronWorks', 6, 480, 1200),
        ('IronWorks', 7, 480, 1200)
     ) AS h (venue_name, iso_weekday, opens, closes)
JOIN venue v ON v.name = h.venue_name;

-- Bookables. Overrides stay null (inherit the type's rules) unless a demo needs otherwise.
INSERT INTO bookable (venue_id, bookable_type_id, name, created_at, updated_at)
SELECT v.id, t.id, b.name, now(), now()
FROM (VALUES
        ('Central Library', 'STUDY', 'ROOM', 'Group Room 1'),
        ('Central Library', 'STUDY', 'ROOM', 'Group Room 2'),
        ('Central Library', 'STUDY', 'ROOM', 'Group Room 3'),
        ('Central Library', 'STUDY', 'ROOM', 'Group Room 4'),
        ('Sunrise Turf',    'SPORTS', 'COURT', 'Court A'),
        ('Sunrise Turf',    'SPORTS', 'COURT', 'Court B'),
        ('CityCare Clinic', 'CLINIC', 'DOCTOR', 'Dr. Rao'),
        ('CityCare Clinic', 'CLINIC', 'DOCTOR', 'Dr. Mehta'),
        ('IronWorks',       'GYM', 'TRAINER', 'Asha (personal training)'),
        ('IronWorks',       'GYM', 'TRAINER', 'Tom (personal training)')
     ) AS b (venue_name, category_code, type_code, name)
JOIN venue v ON v.name = b.venue_name
JOIN category c ON c.code = b.category_code
JOIN bookable_type t ON t.category_id = c.id AND t.code = b.type_code;

-- Library quiet-zone seats Q1..Q12 and visitor bays 1..8.
INSERT INTO bookable (venue_id, bookable_type_id, name, created_at, updated_at)
SELECT v.id, t.id, 'Seat Q' || n, now(), now()
FROM generate_series(1, 12) AS n
JOIN venue v ON v.name = 'Central Library'
JOIN category c ON c.code = 'STUDY'
JOIN bookable_type t ON t.category_id = c.id AND t.code = 'SEAT';

INSERT INTO bookable (venue_id, bookable_type_id, name, created_at, updated_at)
SELECT v.id, t.id, 'Visitor Bay ' || n, now(), now()
FROM generate_series(1, 8) AS n
JOIN venue v ON v.name = 'Palm Apartments'
JOIN category c ON c.code = 'PARKING'
JOIN bookable_type t ON t.category_id = c.id AND t.code = 'BAY';

-- Dr. Mehta has own hours (Tue and Thu 10:00-14:00), which replace the clinic's hours (FR-CAT-04).
INSERT INTO bookable_opening_hours (bookable_id, iso_weekday, opens_at_minute, closes_at_minute)
SELECT b.id, h.iso_weekday, 600, 840
FROM bookable b, (VALUES (2), (4)) AS h (iso_weekday)
WHERE b.name = 'Dr. Mehta';
