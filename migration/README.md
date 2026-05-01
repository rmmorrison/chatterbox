# Legacy Bot Migration

One-shot migration from the old chatterbox PostgreSQL database (tables
`copypasta`, `shouts`, `shout_histories`) into the current schema
(`auto_replies`, `shouts`, `shout_history`).

There is no direct connectivity between the two servers, so the flow is:

1. Run `pg_dump` on the old server, piping through `sed` to retarget the
   dump into a `legacy.*` schema. This avoids a name collision with the
   new `shouts` table.
2. Copy the resulting `legacy_dump.sql` to the new server, alongside
   `migrate.sql`.
3. Run `migrate.sql` against the new database. It creates a `legacy`
   schema, loads the dump into it, transforms the rows into the new
   schema, and drops `legacy` on success. Everything runs in a single
   transaction.

## Step 1 — export (run on the old server)

```bash
pg_dump \
    --data-only \
    --column-inserts \
    --no-owner \
    --no-privileges \
    -t public.copypasta \
    -t public.shouts \
    -t public.shout_histories \
    chatterbox \
  | sed -E '/^SELECT pg_catalog\.setval/d; s/\bpublic\.(copypasta|shouts|shout_histories)\b/legacy.\1/g' \
  > legacy_dump.sql
```

The sed pipeline does two things: drops `setval(...)` calls (the staging
schema has no sequences) and rewrites the qualified table names to
`legacy.*` so the dump can be loaded without colliding with the new
`public.shouts`.

Adjust the database name (`chatterbox`) and add `-h`, `-U`, `-p` as
needed. The output is a portable SQL file containing only INSERTs into
`legacy.*`.

## Step 2 — transfer

`scp legacy_dump.sql user@new-server:/path/to/migration/`

Make sure `legacy_dump.sql` ends up in the same directory as
`migrate.sql` so the `\i` include resolves.

## Step 3 — import (run on the new server)

```bash
psql -d chatterbox -v ON_ERROR_STOP=1 -f migrate.sql
```

`ON_ERROR_STOP=1` ensures any failure aborts the transaction.

## Field mapping

### copypasta → auto_replies
- `trigger`     → `pattern`
- `copypasta`   → `response`
- `description` ← empty string (old table had no equivalent)
- `created_by`  ← `169128347874492417` (provided)
- `created_at`  ← `now()`
- `edited_by`, `edited_at` ← NULL

### shouts → shouts
- `message_id`, `channel_id`, `author_id`, `content` → direct
- `authored_at` ← derived from the Discord snowflake in `message_id`
  (`((message_id >> 22) + 1420070400000)` ms since epoch)
- `created_at`  ← `now()`
- `deleted_at`, `deleted_by` ← NULL
- **Dedup**: the new schema enforces `UNIQUE (channel_id, content)`. Old
  data may contain duplicates; the script keeps the row with the
  smallest `message_id` per `(channel_id, content)`.

### shout_histories → shout_history
- Old `channel_id` (PK) → new `channel_id`
- Old `message_id` → resolved to new `shouts.id` by joining
  `(channel_id, content)`. This handles the case where the specific
  `message_id` was deduped out.
- `emitted_at` ← `now()` (real emission time was not stored).

## Re-running

If anything fails, the wrapping `BEGIN ... COMMIT` rolls back. To re-run
cleanly, drop any partially-loaded rows or simply truncate the three new
tables (if no production data has accumulated yet) and run again.
