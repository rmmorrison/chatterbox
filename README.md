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

Generated jOOQ classes are not checked in. To regenerate them from the union of
all module migrations:

```sh
mvn -Pcodegen generate-sources
```

This profile spins up an ephemeral PostgreSQL via Testcontainers, applies every
migration under `src/main/resources/db/migration/**`, and runs the jOOQ codegen
tool against the resulting schema. **Requires Docker.** Output lands in
`target/generated-sources/jooq/` under
`ca.ryanmorrison.chatterbox.db.generated`.

Re-run this whenever you add or modify a migration.

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
    Set<GatewayIntent> intents();          // unioned across modules
    EnumSet<CacheFlag> cacheFlags();       // unioned across modules
    List<SlashCommandData> slashCommands();
    List<EventListener> listeners();
    List<String> migrationLocations();     // classpath dirs for Flyway
    void onStart(ModuleContext ctx);
    void onStop();
}
```

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
   - Drop migrations under `src/main/resources/db/migration/<name>/`.
   - Return that path from `migrationLocations()`.
   - Run `mvn -Pcodegen generate-sources` to regenerate jOOQ classes.
