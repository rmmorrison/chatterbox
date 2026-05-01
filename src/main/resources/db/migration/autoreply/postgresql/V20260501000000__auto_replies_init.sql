CREATE TABLE auto_replies (
    id           BIGSERIAL    PRIMARY KEY,
    channel_id   BIGINT       NOT NULL,
    pattern      TEXT         NOT NULL,
    response     TEXT         NOT NULL,
    description  TEXT         NOT NULL,
    created_by   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    edited_by    BIGINT,
    edited_at    TIMESTAMPTZ
);

CREATE UNIQUE INDEX auto_replies_channel_pattern_uniq ON auto_replies (channel_id, pattern);
CREATE INDEX auto_replies_channel_idx ON auto_replies (channel_id);
