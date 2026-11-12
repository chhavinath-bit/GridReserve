-- M1.13b (D-48, replaces the free-text city of V6): a venue's city is a row of a curated list, not text an
-- admin types. Free text let "Delhi", "delhi " and typos become different places in the Browse filter.
-- Each city has one IANA zone, so a venue's country and zone now follow from its city.

CREATE TABLE city (
    id            bigint       GENERATED ALWAYS AS IDENTITY,
    country_code  char(2)      NOT NULL,
    name          varchar(100) NOT NULL,
    time_zone     varchar(64)  NOT NULL,
    CONSTRAINT pk_city PRIMARY KEY (id),
    -- The city's zone must be one of its country's (like fk_venue_country_zone).
    CONSTRAINT fk_city_country_zone FOREIGN KEY (country_code, time_zone)
        REFERENCES country_time_zone (country_code, time_zone),
    CONSTRAINT ck_city_name_trimmed CHECK (btrim(name) <> '' AND name = btrim(name)),
    -- Target of fk_venue_city below (a foreign key needs a unique key on exactly its columns).
    CONSTRAINT uq_city_id_country_zone UNIQUE (id, country_code, time_zone)
);

-- One city per name and country in any letter case ("delhi" can't sit next to "Delhi"). An expression
-- can't be a UNIQUE constraint, so this is a unique index; PostgreSQL reports its name as the constraint.
CREATE UNIQUE INDEX uq_city_country_name ON city (country_code, lower(name));

-- The list members and admins pick from: cities in the countries of the demo venues (India, United
-- Kingdom) and a few large ones elsewhere. A new city is added by a migration (an admin screen is later work).
INSERT INTO city (country_code, name, time_zone) VALUES
    ('IN', 'Agra', 'Asia/Kolkata'),
    ('IN', 'Ahmedabad', 'Asia/Kolkata'),
    ('IN', 'Amritsar', 'Asia/Kolkata'),
    ('IN', 'Bengaluru', 'Asia/Kolkata'),
    ('IN', 'Bhopal', 'Asia/Kolkata'),
    ('IN', 'Bhubaneswar', 'Asia/Kolkata'),
    ('IN', 'Chandigarh', 'Asia/Kolkata'),
    ('IN', 'Chennai', 'Asia/Kolkata'),
    ('IN', 'Coimbatore', 'Asia/Kolkata'),
    ('IN', 'Dehradun', 'Asia/Kolkata'),
    ('IN', 'Delhi', 'Asia/Kolkata'),
    ('IN', 'Gurugram', 'Asia/Kolkata'),
    ('IN', 'Guwahati', 'Asia/Kolkata'),
    ('IN', 'Hyderabad', 'Asia/Kolkata'),
    ('IN', 'Indore', 'Asia/Kolkata'),
    ('IN', 'Jaipur', 'Asia/Kolkata'),
    ('IN', 'Kanpur', 'Asia/Kolkata'),
    ('IN', 'Kochi', 'Asia/Kolkata'),
    ('IN', 'Kolkata', 'Asia/Kolkata'),
    ('IN', 'Lucknow', 'Asia/Kolkata'),
    ('IN', 'Ludhiana', 'Asia/Kolkata'),
    ('IN', 'Madurai', 'Asia/Kolkata'),
    ('IN', 'Mangaluru', 'Asia/Kolkata'),
    ('IN', 'Mumbai', 'Asia/Kolkata'),
    ('IN', 'Mysuru', 'Asia/Kolkata'),
    ('IN', 'Nagpur', 'Asia/Kolkata'),
    ('IN', 'Nashik', 'Asia/Kolkata'),
    ('IN', 'Noida', 'Asia/Kolkata'),
    ('IN', 'Panaji', 'Asia/Kolkata'),
    ('IN', 'Patna', 'Asia/Kolkata'),
    ('IN', 'Pune', 'Asia/Kolkata'),
    ('IN', 'Raipur', 'Asia/Kolkata'),
    ('IN', 'Ranchi', 'Asia/Kolkata'),
    ('IN', 'Surat', 'Asia/Kolkata'),
    ('IN', 'Thiruvananthapuram', 'Asia/Kolkata'),
    ('IN', 'Vadodara', 'Asia/Kolkata'),
    ('IN', 'Varanasi', 'Asia/Kolkata'),
    ('IN', 'Visakhapatnam', 'Asia/Kolkata'),
    ('GB', 'Belfast', 'Europe/London'),
    ('GB', 'Birmingham', 'Europe/London'),
    ('GB', 'Bristol', 'Europe/London'),
    ('GB', 'Cambridge', 'Europe/London'),
    ('GB', 'Cardiff', 'Europe/London'),
    ('GB', 'Edinburgh', 'Europe/London'),
    ('GB', 'Glasgow', 'Europe/London'),
    ('GB', 'Leeds', 'Europe/London'),
    ('GB', 'Liverpool', 'Europe/London'),
    ('GB', 'London', 'Europe/London'),
    ('GB', 'Manchester', 'Europe/London'),
    ('GB', 'Newcastle upon Tyne', 'Europe/London'),
    ('GB', 'Nottingham', 'Europe/London'),
    ('GB', 'Oxford', 'Europe/London'),
    ('GB', 'Sheffield', 'Europe/London'),
    ('AE', 'Dubai', 'Asia/Dubai'),
    ('AU', 'Melbourne', 'Australia/Melbourne'),
    ('AU', 'Sydney', 'Australia/Sydney'),
    ('CA', 'Toronto', 'America/Toronto'),
    ('CA', 'Vancouver', 'America/Vancouver'),
    ('DE', 'Berlin', 'Europe/Berlin'),
    ('FR', 'Paris', 'Europe/Paris'),
    ('JP', 'Tokyo', 'Asia/Tokyo'),
    ('NL', 'Amsterdam', 'Europe/Amsterdam'),
    ('SG', 'Singapore', 'Asia/Singapore'),
    ('US', 'Chicago', 'America/Chicago'),
    ('US', 'Los Angeles', 'America/Los_Angeles'),
    ('US', 'New York', 'America/New_York'),
    ('US', 'San Francisco', 'America/Los_Angeles');

-- Venues whose text city is not in the list (only possible in local databases: the seeded venues are in
-- Pune, Bengaluru, Chennai, Hyderabad and London) keep their place: their city is added once per country,
-- with the zone of the oldest such venue. No venue is changed or left without a city.
INSERT INTO city (country_code, name, time_zone)
SELECT DISTINCT ON (v.country_code, lower(btrim(v.city))) v.country_code, btrim(v.city), v.time_zone
FROM venue v
WHERE NOT EXISTS (SELECT 1 FROM city c
                  WHERE c.country_code = v.country_code AND lower(c.name) = lower(btrim(v.city)))
ORDER BY v.country_code, lower(btrim(v.city)), v.id;

ALTER TABLE venue ADD COLUMN city_id bigint;

UPDATE venue v
SET city_id = c.id
FROM city c
WHERE c.country_code = v.country_code AND lower(c.name) = lower(btrim(v.city));

ALTER TABLE venue ALTER COLUMN city_id SET NOT NULL;

-- The venue's country and zone are the city's: one composite foreign key, so they can't disagree.
-- (A local venue whose zone differs from its city's makes this statement fail, on purpose: fix it by hand.)
ALTER TABLE venue ADD CONSTRAINT fk_venue_city FOREIGN KEY (city_id, country_code, time_zone)
    REFERENCES city (id, country_code, time_zone);

DROP INDEX ix_venue_country_city;
ALTER TABLE venue DROP CONSTRAINT ck_venue_city_not_blank;
ALTER TABLE venue DROP COLUMN city;

-- The Browse filter asks for one city.
CREATE INDEX ix_venue_city ON venue (city_id);
