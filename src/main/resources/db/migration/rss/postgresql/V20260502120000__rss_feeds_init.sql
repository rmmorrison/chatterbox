CREATE TABLE rss_feeds (
    id                     BIGSERIAL    PRIMARY KEY,
    guild_id               BIGINT       NOT NULL,
    channel_id             BIGINT       NOT NULL,
    url                    TEXT         NOT NULL,
    title                  TEXT         NOT NULL,
    added_by               BIGINT       NOT NULL,
    refresh_minutes        INTEGER      NOT NULL,
    last_item_id           TEXT,
    last_item_published_at TIMESTAMPTZ,
    last_refreshed_at      TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX rss_feeds_channel_url_uniq ON rss_feeds (channel_id, url);
CREATE INDEX rss_feeds_channel_idx ON rss_feeds (channel_id);
