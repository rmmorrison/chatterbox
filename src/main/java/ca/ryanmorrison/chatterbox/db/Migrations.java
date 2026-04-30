package ca.ryanmorrison.chatterbox.db;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;

/**
 * Runs Flyway over the union of migration locations declared by every module.
 * All locations share a single {@code flyway_schema_history} table; modules
 * use timestamp-based version prefixes to avoid collisions.
 */
public final class Migrations {

    private static final Logger log = LoggerFactory.getLogger(Migrations.class);

    private Migrations() {}

    public static void run(DataSource ds, List<String> classpathLocations) {
        if (classpathLocations.isEmpty()) {
            log.info("No migration locations registered; skipping Flyway.");
            return;
        }
        String[] locations = classpathLocations.stream()
                .map(loc -> loc.startsWith("classpath:") ? loc : "classpath:" + loc)
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
}
