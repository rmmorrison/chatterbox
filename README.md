# Chatterbox

Modular Discord bot built on [JDA](https://jda.wiki/) with a self-registering
feature ("module") system, optional Postgres/SQLite persistence via jOOQ +
Flyway, and a containerised deployment story.

## Requirements

- **JDK 25** (LTS) — enforced by the build.
- **Maven 3.9+**.
- **Docker** — only required when running the jOOQ codegen profile or the
  containerised image.

## Configuration

All configuration is environment-variable based, suitable for containerised
deployment.

| Variable                   | Required | Default | Purpose |
|----------------------------|----------|---------|---------|
| `CHATTERBOX_DISCORD_TOKEN` | yes      | —       | Bot token. |
| `CHATTERBOX_DB_URL`        | yes      | —       | JDBC URL. `jdbc:postgresql:...` or `jdbc:sqlite:...`. |
| `CHATTERBOX_DB_USER`       | no       | —       | DB user (Postgres). Unused for SQLite. |
| `CHATTERBOX_DB_PASSWORD`   | no       | —       | DB password (Postgres). Unused for SQLite. |
| `CHATTERBOX_DEV_MODE`      | no       | `false` | When `true`, slash commands register per-guild for instant updates. When `false`, they register globally. The opposite scope is cleared on each startup, so switching modes never leaves duplicates. |
| `CHATTERBOX_LOG_LEVEL`     | no       | `INFO`  | Root logger level. |

## Building

```sh
mvn -B verify        # compile, test, and produce target/chatterbox.jar (fat)
```

The fat JAR is produced by `maven-shade-plugin` with
`ServicesResourceTransformer` so module SPI registrations from every dependency
are merged correctly.

### jOOQ code generation

Generated jOOQ classes live under `src/generated/jooq/` and **are committed to
the repository** as a build artifact. Default builds add the directory to the
compile path; no Docker is needed to build the project.

To regenerate after adding or modifying a migration:

```sh
mvn -Pcodegen generate-sources
```

This profile spins up an ephemeral PostgreSQL via Testcontainers, applies every
migration under `src/main/resources/db/migration/**/postgresql/`, and runs jOOQ
codegen against the resulting schema. **Requires Docker.** Output replaces
`src/generated/jooq/`. Commit the regenerated sources alongside the migration.

The same generated classes are used at runtime against both Postgres and
SQLite — the runtime `DSLContext` disables schema rendering for SQLite so the
Postgres `public.` schema prefix is stripped automatically.

## Container build

```sh
docker build -t chatterbox .
docker run --rm \
  -e CHATTERBOX_DISCORD_TOKEN=... \
  -e CHATTERBOX_DEV_MODE=true \
  chatterbox
```

## Dropbox backup sync

The `backup-sync` service in `docker-compose.yml` mirrors the local
`postgres-backups` volume to a Dropbox App folder using
[rclone](https://rclone.org/). It is gated behind the `dropbox-sync` Compose
profile so it only runs when explicitly enabled.

### One-time Dropbox app setup

1. Go to https://www.dropbox.com/developers/apps and **Create app**:
   - API: **Scoped access**
   - Type: **App folder** (limits rclone to `Apps/<your-app-name>/`)
   - Name: anything unique, e.g. `chatterbox-backups`
2. On the new app's **Permissions** tab, enable:
   - `files.content.write`
   - `files.content.read`
   - `files.metadata.write`
   - `files.metadata.read`
   Click **Submit** to save.
3. On the **Settings** tab, copy the **App key** and **App secret**.

### Generate the rclone token

rclone's OAuth flow needs a browser to reach `http://127.0.0.1:53682/` on
the host running rclone. On a headless VPS, the trick is to tunnel that
port back to your workstation over SSH so your local browser can reach the
container running remotely.

**1. From your workstation**, open an SSH session to the VPS that forwards
the rclone callback port:

```sh
ssh -L 53682:localhost:53682 you@your-vps
```

Leave that session open for the rest of the flow.

**2. Inside that SSH session**, run rclone in Docker on the VPS:

```sh
docker run --rm -it -p 53682:53682 rclone/rclone:1.68 \
  authorize "dropbox" "<app key>" "<app secret>"
```

**3. Copy the URL it prints** (looks like
`https://www.dropbox.com/oauth2/authorize?...&redirect_uri=http://localhost:53682/`)
and paste it into the browser **on your workstation**. Approve the Dropbox
prompt; the redirect to `localhost:53682` travels through the SSH tunnel
and is caught by the container on the VPS.

rclone then prints a JSON object on stdout — that is the value for
`DROPBOX_TOKEN`. Paste it into `.env` on the VPS. rclone refreshes the
access token automatically, so this only needs to be done once per app.

### Configure and run

Fill in the four `DROPBOX_*` values in `.env`, then start the profile:

```sh
docker compose --profile dropbox-sync up -d
```

The sidecar mounts `postgres-backups` read-only, runs `rclone sync` every
`BACKUP_SYNC_INTERVAL` seconds (default 3600), and mirrors deletions —
which means the existing `BACKUP_KEEP_*` retention propagates to Dropbox
automatically and keeps you under the 2 GB free-tier limit.

| Variable               | Required | Default               | Purpose |
|------------------------|----------|-----------------------|---------|
| `DROPBOX_CLIENT_ID`    | yes      | —                     | App key from the Dropbox app settings page. |
| `DROPBOX_CLIENT_SECRET`| yes      | —                     | App secret from the Dropbox app settings page. |
| `DROPBOX_TOKEN`        | yes      | —                     | OAuth token JSON produced by `rclone authorize`. |
| `DROPBOX_REMOTE_PATH`  | no       | `chatterbox-backups`  | Folder inside the app folder to sync into. |
| `BACKUP_SYNC_INTERVAL` | no       | `3600`                | Seconds between sync runs. |

## Architecture

### Module SPI

Features ("modules") implement
[`ca.ryanmorrison.chatterbox.module.Module`](src/main/java/ca/ryanmorrison/chatterbox/module/Module.java)
and self-register via
`META-INF/services/ca.ryanmorrison.chatterbox.module.Module`. Each module
declares only what it actually needs:

```java
public interface Module {
    String name();
    Set<GatewayIntent> intents();              // unioned across modules
    EnumSet<CacheFlag> cacheFlags();           // unioned across modules
    List<SlashCommandData> slashCommands(InitContext);
    List<EventListener> listeners(InitContext);
    List<String> migrationLocations();         // classpath dirs for Flyway
    void onStart(ModuleContext ctx);           // post-JDA-ready hook
    void onStop();
}
```

`InitContext` exposes the database and config so listeners and command
builders can be constructed fully wired before JDA is built. `ModuleContext`
adds the live `JDA` reference for post-ready actions.

Adding a feature is two files: an implementation class plus one line in the
service file. There is no central registration table to edit.

### Slash command sync

`CommandSync` runs at startup and on `GuildJoinEvent`. It always uses JDA's
full-replacement `updateCommands()` and always clears the inactive scope:

- **dev**:  push commands to every guild, clear globals
- **prod**: push commands globally, clear every guild override

This guarantees there are never stale commands left in the wrong scope when
flipping `CHATTERBOX_DEV_MODE`.

### Database

The database is part of the bot's runtime: the connection pool is initialised
at startup and `ModuleContext.database()` is always available. Whether a
module uses it is up to the module.

A module that needs persistence:

1. Declares its migration locations from `migrationLocations()`.
2. Resolves a `DSLContext` from `ctx.database()` (no `Optional`).

Migrations from every module are merged into a single Flyway run with one
shared `flyway_schema_history` table. To avoid version collisions, use
**timestamp-based version numbers** (e.g. `V20260430120000__counters_init.sql`)
with one folder per module:

```
src/main/resources/db/migration/
├── ping/V20260430120000__counters.sql
└── karma/V20260501090000__karma.sql
```

The dialect is selected from the JDBC URL (`postgresql` → `POSTGRES`,
`sqlite` → `SQLITE`).

### Logging

SLF4J 2 + Logback. `java.util.logging` is bridged through `jul-to-slf4j` so
output from JDBC drivers flows to the same place. The root level is driven by
`CHATTERBOX_LOG_LEVEL`.

## Adding a new module

1. Create `src/main/java/ca/ryanmorrison/chatterbox/features/<name>/<Name>Module.java`
   implementing `Module`.
2. Append the FQCN to
   `src/main/resources/META-INF/services/ca.ryanmorrison.chatterbox.module.Module`.
3. If the module needs persistence:
   - Drop migrations under
     `src/main/resources/db/migration/<name>/postgresql/V<timestamp>__<desc>.sql`
     and the matching SQLite version under `db/migration/<name>/sqlite/`.
   - Return `db/migration/<name>` from `migrationLocations()`.
   - Run `mvn -Pcodegen generate-sources` to regenerate jOOQ classes and
     commit the result.

## Built-in features

### Shout

Listens for guild messages and "shouts back" — when a user posts an all-caps
message, the bot stores it (per channel) and replies with a random previously
stored shout from the same channel. See
[`ShoutDetector`](src/main/java/ca/ryanmorrison/chatterbox/features/shout/ShoutDetector.java)
for the classifier rules.

Requires the **`MESSAGE_CONTENT`** privileged intent — enable it on the bot's
application page in the Discord Developer Portal.

#### `/shout-history`

Each successful emission is recorded against the channel. Users can run the
ephemeral, guild-only `/shout-history` slash command to browse the bot's
emissions for the current channel, paginating with **← Older** / **Newer →**
buttons. The embed shows the original author's server display name, the time
the shout was first written, and a position indicator (e.g. `Entry 2 of 17`).

History rows are tied to their underlying shout via an FK with
`ON DELETE CASCADE`, so any path that removes a shout (delete, edit-disqualifies,
edit-collision, bulk delete) automatically removes the corresponding history
entries.

#### Moderation (Manage Messages)

Users with the **Manage Messages** permission in a channel see an additional
**Delete** button while paginating that channel's history. Clicking it
soft-deletes the shout — the row stays in the database with a `deleted_at`
timestamp and the moderator's user id stamped into `deleted_by`. Soft-deleted
shouts:

- never appear in random peer selection, regardless of viewer.
- are filtered out of history for non-moderators.
- remain visible to moderators with a red-tinted embed and a `Deleted by` field.

For moderators viewing a deleted entry the button slot becomes **Restore**
(green); clicking it clears both `deleted_at` and `deleted_by`, returning the
shout to circulation. The position indicator reflects what the viewer can
see, so a moderator's `Entry X of N` may have a larger `N` than a
non-moderator's in the same channel.

The permission is re-checked on every interaction (slash and button), so
losing the role mid-pagination immediately removes the moderation buttons.

### Autoreply

Channel-scoped automated replies. Moderators (Manage Messages) configure
regex → response rules with `/autoreply add|edit|delete`; the listener
scans incoming guild messages and, on the first matching rule, posts that
rule's response.

Schema: each rule stores its `pattern`, `response`, `description`, plus
audit columns for the original creator + creation time and (when edited)
the editor + edit time. Patterns are unique per `(channel_id, pattern)`.

#### Subcommands

- **`/autoreply add`** opens a modal with three fields: pattern (single
  line), description (single line), response (multi-line). All three are
  validated server-side: the pattern must compile, none may be blank, and
  each has a length cap. If the submitted pattern already exists in the
  channel, the user is prompted with **Override** / **Cancel** buttons —
  Override rewrites the existing row's response and description.
- **`/autoreply edit`** lists existing rules in an ephemeral string-select
  menu (capped at 25 most-recently-touched, per Discord). Selecting a
  rule opens an edit modal pre-filled with its current pattern,
  description, and response.
- **`/autoreply delete`** lists rules in the same way; selecting one
  shows a confirmation prompt with **Delete** / **Cancel** buttons.

#### Match semantics

When a guild message arrives, rules for the channel are tried in
insertion order (lowest id first); the first rule whose regex finds a
match in the message wins, and its response is posted as a plain message.
Subsequent rules are not evaluated. The bot ignores messages from other
bots and itself, and DMs.

#### ReDoS protection

Every match runs against a `WatchdogCharSequence` that caps a single
match attempt at ~100ms. If a moderator configures a catastrophically
backtracking pattern, evaluation aborts, the rule is skipped (with a
warning logged), and the next rule is tried. This is cheap mitigation,
not a hard guarantee — sufficient for a Manage-Messages-only feature.

#### Permissions

`MESSAGE_MANAGE` in the channel is required for every interaction
(slash, modal, select, button). Permissions are re-checked on every
event, so losing the role mid-flow denies further actions immediately.
