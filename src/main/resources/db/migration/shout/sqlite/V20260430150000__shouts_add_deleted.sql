ALTER TABLE shouts ADD COLUMN deleted_at TEXT;
ALTER TABLE shouts ADD COLUMN deleted_by INTEGER;

CREATE INDEX shouts_active_idx ON shouts (channel_id) WHERE deleted_at IS NULL;
