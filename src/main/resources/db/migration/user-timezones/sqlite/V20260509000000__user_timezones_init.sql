CREATE TABLE user_timezones (
    user_id    INTEGER NOT NULL,
    zone_id    TEXT    NOT NULL,
    updated_at TEXT    NOT NULL,
    PRIMARY KEY (user_id)
);
