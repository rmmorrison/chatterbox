CREATE TABLE runtime_config (
    guild_id   BIGINT       NOT NULL,
    key        TEXT         NOT NULL,
    value      TEXT         NOT NULL,
    updated_by BIGINT       NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (guild_id, key)
);
