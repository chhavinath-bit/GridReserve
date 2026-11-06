-- btree_gist lets a GiST exclusion constraint combine "=" on a plain column (bookable_id, member_id)
-- with "&&" (overlaps) on a time range. Without it, double-booking could not be enforced by the database.
CREATE EXTENSION IF NOT EXISTS btree_gist;
