-- Click analytics: per-token redirect counter and last-click timestamp.
-- Counter increments atomically on each live-token redirect; soft-deleted
-- hits return 410 and don't bump the count.
ALTER TABLE shortened_urls ADD COLUMN click_count     INTEGER NOT NULL DEFAULT 0;
ALTER TABLE shortened_urls ADD COLUMN last_clicked_at TEXT;
