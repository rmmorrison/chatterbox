-- Legacy bot migration: copypasta/shouts/shout_histories -> auto_replies/shouts/shout_history.
-- Run on the new database after placing legacy_dump.sql in the same directory.
--   psql -d chatterbox -v ON_ERROR_STOP=1 -f migrate.sql

\set ON_ERROR_STOP on

BEGIN;

-- 1. Staging area for the legacy dump. The dump was rewritten at export time
--    to reference legacy.* (instead of public.*) to avoid colliding with the
--    new public.shouts table.

DROP SCHEMA IF EXISTS legacy CASCADE;
CREATE SCHEMA legacy;

CREATE TABLE legacy.copypasta (
    id         INTEGER                NOT NULL PRIMARY KEY,
    channel_id BIGINT                 NOT NULL,
    trigger    VARCHAR(255)           NOT NULL,
    copypasta  TEXT                   NOT NULL
);

CREATE TABLE legacy.shouts (
    message_id BIGINT NOT NULL PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    author_id  BIGINT NOT NULL,
    content    TEXT   NOT NULL
);

CREATE TABLE legacy.shout_histories (
    channel_id BIGINT NOT NULL PRIMARY KEY,
    message_id BIGINT NOT NULL UNIQUE
);

-- 2. Load the data-only dump produced by pg_dump on the old server.
--    \ir resolves relative to the location of this script, so legacy_dump.sql
--    just needs to sit next to migrate.sql.
\ir legacy_dump.sql

-- pg_dump's preamble sets search_path='' and client_min_messages=warning;
-- restore both so the rest of this script behaves normally.
SET search_path TO public;
SET client_min_messages TO notice;

-- 3. copypasta -> auto_replies.
--    description is NOT NULL on the new schema; the old table had no equivalent,
--    so we seed it with the trigger pattern itself - that gives editors something
--    to recognize the rule by until someone writes a real description.
--    created_by is the operator-supplied bot owner ID.

INSERT INTO public.auto_replies (channel_id, pattern, response, description, created_by)
SELECT channel_id, trigger, copypasta, trigger, 169128347874492417
FROM legacy.copypasta
ON CONFLICT (channel_id, pattern) DO NOTHING;

-- 4. shouts -> shouts.
--    The new schema enforces UNIQUE (channel_id, content); old data may contain
--    duplicates, so we keep the row with the smallest message_id per
--    (channel_id, content). authored_at is derived from the Discord snowflake.

INSERT INTO public.shouts (channel_id, message_id, content, author_id, authored_at)
SELECT DISTINCT ON (channel_id, content)
       channel_id,
       message_id,
       content,
       author_id,
       to_timestamp(((message_id >> 22) + 1420070400000) / 1000.0)
FROM legacy.shouts
ORDER BY channel_id, content, message_id
ON CONFLICT (message_id) DO NOTHING;

-- 5. shout_histories -> shout_history.
--    Old shout_histories pointed at a message_id that may have been deduped
--    out of the new shouts table, so we resolve via (channel_id, content).

INSERT INTO public.shout_history (channel_id, shout_id)
SELECT h.channel_id, s.id
FROM legacy.shout_histories h
JOIN legacy.shouts ls ON ls.message_id = h.message_id
JOIN public.shouts s   ON s.channel_id = ls.channel_id
                      AND s.content    = ls.content;

-- 6. Sanity report. Counts visible in the psql output before commit.
DO $$
DECLARE
    cp_old BIGINT; cp_new BIGINT;
    sh_old BIGINT; sh_new BIGINT;
    hi_old BIGINT; hi_new BIGINT;
BEGIN
    SELECT count(*) INTO cp_old FROM legacy.copypasta;
    SELECT count(*) INTO cp_new FROM public.auto_replies;
    SELECT count(*) INTO sh_old FROM legacy.shouts;
    SELECT count(*) INTO sh_new FROM public.shouts;
    SELECT count(*) INTO hi_old FROM legacy.shout_histories;
    SELECT count(*) INTO hi_new FROM public.shout_history;
    RAISE NOTICE 'copypasta: % legacy rows -> % auto_replies rows', cp_old, cp_new;
    RAISE NOTICE 'shouts:    % legacy rows -> % shouts rows (dedup may reduce)', sh_old, sh_new;
    RAISE NOTICE 'history:   % legacy rows -> % shout_history rows', hi_old, hi_new;
END $$;

DROP SCHEMA legacy CASCADE;

COMMIT;
