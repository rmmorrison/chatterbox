CREATE TABLE shout_history (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id  INTEGER NOT NULL,
    shout_id    INTEGER NOT NULL REFERENCES shouts(id) ON DELETE CASCADE,
    emitted_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX shout_history_channel_id_idx ON shout_history (channel_id, id DESC);
CREATE INDEX shout_history_shout_id_idx   ON shout_history (shout_id);
