CREATE TABLE rss_feeds (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id               INTEGER NOT NULL,
    channel_id             INTEGER NOT NULL,
    url                    TEXT    NOT NULL,
    title                  TEXT    NOT NULL,
    added_by               INTEGER NOT NULL,
    refresh_minutes        INTEGER NOT NULL,
    last_item_id           TEXT,
    last_item_published_at TEXT,
    last_refreshed_at      TEXT,
    created_at             TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE UNIQUE INDEX rss_feeds_channel_url_uniq ON rss_feeds (channel_id, url);
CREATE INDEX rss_feeds_channel_idx ON rss_feeds (channel_id);
