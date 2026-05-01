CREATE TABLE shouts (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id  INTEGER NOT NULL,
    message_id  INTEGER NOT NULL UNIQUE,
    content     TEXT    NOT NULL,
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE UNIQUE INDEX shouts_channel_content_uniq ON shouts (channel_id, content);
CREATE INDEX shouts_channel_idx ON shouts (channel_id);
