ALTER TABLE shortened_urls ADD COLUMN deleted_at TEXT;
ALTER TABLE shortened_urls ADD COLUMN deleted_by INTEGER;

-- Replace the strict url uniqueness with a partial index so deleting a row
-- frees up its destination URL for re-shortening (with a fresh token). The
-- token unique index stays unconditional so deleted tokens can never be
-- reissued.
DROP INDEX shortened_urls_url_uniq;
CREATE UNIQUE INDEX shortened_urls_url_uniq
    ON shortened_urls (url)
    WHERE deleted_at IS NULL;
