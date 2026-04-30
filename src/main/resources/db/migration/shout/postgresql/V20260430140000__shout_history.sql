CREATE TABLE shout_history (
    id          BIGSERIAL    PRIMARY KEY,
    channel_id  BIGINT       NOT NULL,
    shout_id    BIGINT       NOT NULL REFERENCES shouts(id) ON DELETE CASCADE,
    emitted_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX shout_history_channel_id_idx ON shout_history (channel_id, id DESC);
CREATE INDEX shout_history_shout_id_idx   ON shout_history (shout_id);
