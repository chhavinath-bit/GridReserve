-- M1.13 (D-46): a venue's city, so members can filter venues by place ("Hyderabad, India") and see where they
-- are booking (FR-CAT-16, FR-UI-14). Required from now on; admins enter it with the address.

ALTER TABLE venue ADD COLUMN city varchar(100);

-- Existing venues: the part after the last comma of the address ("12 Lake View Road, Pune" -> "Pune"), else
-- "Unknown" for an admin to correct. The address itself is left as it was.
UPDATE venue
SET city = CASE
               WHEN position(',' IN coalesce(address_line, '')) > 0
                   THEN nullif(btrim(regexp_replace(address_line, '^.*,', '')), '')
           END;
UPDATE venue SET city = 'Unknown' WHERE city IS NULL;

ALTER TABLE venue ALTER COLUMN city SET NOT NULL;
ALTER TABLE venue ADD CONSTRAINT ck_venue_city_not_blank CHECK (btrim(city) <> '');

-- The Browse filter asks for one country and city, case-insensitively.
CREATE INDEX ix_venue_country_city ON venue (country_code, lower(city));
