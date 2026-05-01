# Legacy Bot Migration

One-shot migration from the old chatterbox PostgreSQL database (tables
`copypasta`, `shouts`, `shout_histories`) into the current schema
(`auto_replies`, `shouts`, `shout_history`).

Both the old and new servers run PostgreSQL via `docker compose`, and
neither host has Postgres client tools installed — every `pg_dump` /
`psql` invocation runs inside the `postgres` container. The two servers
have no direct network path between them, so the flow is:

1. On the **old server**, run `pg_dump` inside its postgres container,
   piping the output through `sed` on the host to retarget the dump
   into a `legacy.*` schema. This avoids colliding with the new
   `public.shouts` table on import.
2. Copy the resulting `legacy_dump.sql` to the **new server**, alongside
   `migrate.sql`.
3. Copy both files into the new server's postgres container and run
   `migrate.sql` from there. It creates a `legacy` schema, loads the
   dump into it, transforms the rows into the new schema, and drops
   `legacy` on success. Everything runs in a single transaction.

The examples below assume `docker compose` is invoked from the directory
containing the project's `docker-compose.yml`. If the project uses
`docker-compose` (hyphenated) substitute that. The postgres service
defaults to user `chatterbox` / database `chatterbox`; if your `.env`
overrides them, substitute or `set -a; source .env; set +a` first.

## Step 1 — export (run on the old server)

```bash
docker compose exec -T postgres pg_dump \
    --data-only \
    --column-inserts \
    --no-owner \
    --no-privileges \
    -U "${POSTGRES_USER:-chatterbox}" \
    -t public.copypasta \
    -t public.shouts \
    -t public.shout_histories \
    "${POSTGRES_DB:-chatterbox}" \
  | sed -E '/^SELECT pg_catalog\.setval/d; s/\bpublic\.(copypasta|shouts|shout_histories)\b/legacy.\1/g' \
  > legacy_dump.sql
```

`-T` disables TTY allocation so the pipe to `sed` works correctly. The
redirect runs on the host, so `legacy_dump.sql` lands on the host
filesystem.

The sed pipeline does two things: drops `setval(...)` calls (the staging
schema has no sequences) and rewrites the qualified table names to
`legacy.*`.

## Step 2 — transfer

```bash
scp legacy_dump.sql migrate.sql user@new-server:/path/to/migration/
```

`legacy_dump.sql` and `migrate.sql` must end up in the same directory
on the new host — `migrate.sql` includes the dump via `\ir` (relative
include).

## Step 3 — import (run on the new server)

Copy both files into the running postgres container, then run the
script from inside it:

```bash
docker compose cp legacy_dump.sql postgres:/tmp/legacy_dump.sql
docker compose cp migrate.sql     postgres:/tmp/migrate.sql

docker compose exec -T postgres psql \
    -U "${POSTGRES_USER:-chatterbox}" \
    -d "${POSTGRES_DB:-chatterbox}" \
    -v ON_ERROR_STOP=1 \
    -f /tmp/migrate.sql

docker compose exec -T postgres rm /tmp/legacy_dump.sql /tmp/migrate.sql
```

`ON_ERROR_STOP=1` aborts the transaction on any failure. The script
emits a `NOTICE` summary of input vs. output row counts before
committing — watch for it in the output.

## Field mapping

### copypasta → auto_replies
- `trigger`     → `pattern`
- `copypasta`   → `response`
- `description` ← copy of `trigger` (old table had no equivalent; this
  gives editors something to identify the rule by until someone writes
  a real description)
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

If anything fails, the wrapping `BEGIN ... COMMIT` rolls back. To
re-run cleanly, truncate the three new tables (if no production data
has accumulated yet) and run again.
