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
