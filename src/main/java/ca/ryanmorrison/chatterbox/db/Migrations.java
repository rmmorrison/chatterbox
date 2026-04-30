package ca.ryanmorrison.chatterbox.db;

import org.flywaydb.core.Flyway;
import org.jooq.SQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;

/**
 * Runs Flyway over the union of migration locations declared by every module,
 * resolving each base location to a dialect-specific subfolder so Postgres and
 * SQLite migrations can coexist.
 *
 * <p>A module that declares {@code db/migration/shout} ships its migrations as:
 * <pre>
 *   db/migration/shout/postgresql/V20260430120000__init.sql
 *   db/migration/shout/sqlite/V20260430120000__init.sql
 * </pre>
 *
 * <p>All resolved locations share a single {@code flyway_schema_history} table.
 * Use timestamp-based version numbers to avoid collisions across modules.
 */
public final class Migrations {

    private static final Logger log = LoggerFactory.getLogger(Migrations.class);

    private Migrations() {}

    public static void run(DataSource ds, List<String> baseLocations, SQLDialect dialect) {
        if (baseLocations.isEmpty()) {
            log.info("No migration locations registered; skipping Flyway.");
            return;
        }
        String suffix = subfolderFor(dialect);
        String[] locations = baseLocations.stream()
                .map(loc -> loc.startsWith("classpath:") ? loc : "classpath:" + loc)
                .map(loc -> loc + "/" + suffix)
                .toArray(String[]::new);

        log.info("Running Flyway over locations: {}", List.of(locations));
        var result = Flyway.configure()
                .dataSource(ds)
                .locations(locations)
                .load()
                .migrate();
        log.info("Flyway applied {} migration(s); schema at version {}",
                result.migrationsExecuted, result.targetSchemaVersion);
    }

    private static String subfolderFor(SQLDialect dialect) {
        return switch (dialect) {
            case POSTGRES -> "postgresql";
            case SQLITE   -> "sqlite";
            default -> throw new IllegalArgumentException("Unsupported dialect: " + dialect);
        };
    }
}
