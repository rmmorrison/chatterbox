ALTER TABLE shouts ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE shouts ADD COLUMN deleted_by BIGINT;

-- Partial index: random-peer / active-history queries always filter
-- deleted_at IS NULL, so a partial index keeps the active set small.
CREATE INDEX shouts_active_idx ON shouts (channel_id) WHERE deleted_at IS NULL;
