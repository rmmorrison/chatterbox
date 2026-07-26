-- Repairs rows left with an empty-string authored_at by
-- V20260430130000__shouts_add_author.sql.
--
-- That migration added authored_at as NOT NULL DEFAULT '' on SQLite, so every
-- pre-existing row was backfilled with '' -- which jOOQ then tries to read as
-- an OffsetDateTime via SHOUTS.AUTHORED_AT (ShoutRepository, ShoutStatsRepository).
--
-- V20260430130000 is already applied in production, so it is deliberately left
-- untouched: editing it would change its checksum and fail Flyway validation on
-- every existing database. This forward migration repairs the data instead.
--
-- created_at is the best available approximation of authoring time: it was set
-- by the insert that recorded the shout, which happens on the message-received
-- event. Rows with a valid authored_at are left alone, so this is a no-op on a
-- database created after V20260430130000.
UPDATE shouts
SET authored_at = created_at
WHERE authored_at = '';

-- author_id's 0 default is left as-is: 0 is not a valid Discord snowflake, so
-- it already reads as an "unknown author" sentinel, and there is no source of
-- truth to recover the real value from.
