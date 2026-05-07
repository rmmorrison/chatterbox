CREATE TABLE runtime_config (
    guild_id   INTEGER NOT NULL,
    key        TEXT    NOT NULL,
    value      TEXT    NOT NULL,
    updated_by INTEGER NOT NULL,
    updated_at TEXT    NOT NULL,
    PRIMARY KEY (guild_id, key)
);
