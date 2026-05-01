CREATE TABLE shouts (
    id          BIGSERIAL    PRIMARY KEY,
    channel_id  BIGINT       NOT NULL,
    message_id  BIGINT       NOT NULL UNIQUE,
    content     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX shouts_channel_content_uniq ON shouts (channel_id, content);
CREATE INDEX shouts_channel_idx ON shouts (channel_id);
