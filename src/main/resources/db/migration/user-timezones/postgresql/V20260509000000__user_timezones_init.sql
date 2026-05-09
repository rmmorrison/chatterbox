CREATE TABLE user_timezones (
    user_id    BIGINT      NOT NULL,
    zone_id    TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id)
);
