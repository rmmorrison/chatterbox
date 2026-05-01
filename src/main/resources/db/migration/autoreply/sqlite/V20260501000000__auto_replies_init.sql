CREATE TABLE auto_replies (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id   INTEGER NOT NULL,
    pattern      TEXT    NOT NULL,
    response     TEXT    NOT NULL,
    description  TEXT    NOT NULL,
    created_by   INTEGER NOT NULL,
    created_at   TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    edited_by    INTEGER,
    edited_at    TEXT
);

CREATE UNIQUE INDEX auto_replies_channel_pattern_uniq ON auto_replies (channel_id, pattern);
CREATE INDEX auto_replies_channel_idx ON auto_replies (channel_id);
