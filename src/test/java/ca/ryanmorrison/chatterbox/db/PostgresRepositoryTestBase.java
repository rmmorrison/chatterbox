package ca.ryanmorrison.chatterbox.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for repository tests that run against a real PostgreSQL container.
 *
 * <p>Exists for two reasons. The obvious one is that the Hikari + Flyway +
 * {@code DSL.using} setup was copy-pasted verbatim across every repository
 * test. The important one is dialect parity: the generated jOOQ classes are
 * produced against PostgreSQL but executed against SQLite in most deployments,
 * so a repository verified against only one dialect is only half verified.
 * Subclass this <em>and</em> {@link SqliteRepositoryTestBase} for anything
 * using constructs whose SQL differs by dialect — {@code charLength},
 * {@code countDistinct}, {@code onConflict}, multi-column {@code GROUP BY}.
 *
 * <p>Subclasses declare which migration folder to apply via
 * {@link #migrationLocation()}.
 */
public abstract class PostgresRepositoryTestBase {

    /**
     * Matches the image used in production (see docker-compose.yml). Tests
     * previously ran 17 while production ran 18.
     */
    private static final String IMAGE = "postgres:18-alpine";

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;

    protected static DSLContext dsl;

    /** Classpath location of this feature's PostgreSQL migrations. */
    protected abstract String migrationLocation();

    @BeforeAll
    static void startContainer() {
        postgres = new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("chatterbox_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        var hc = new HikariConfig();
        hc.setJdbcUrl(postgres.getJdbcUrl());
        hc.setUsername(postgres.getUsername());
        hc.setPassword(postgres.getPassword());
        dataSource = new HikariDataSource(hc);

        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    @AfterAll
    static void stopContainer() {
        if (dataSource != null) dataSource.close();
        if (postgres != null) postgres.stop();
    }

    /**
     * Migrates and truncates before each test. Flyway is idempotent, so running
     * it per-test costs nothing after the first and keeps the base usable
     * without subclasses ordering their own setup.
     */
    @BeforeEach
    void migrateAndReset() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocation())
                .load()
                .migrate();
        truncateAll();
    }

    /** Empties every table Flyway created, leaving the schema in place. */
    private void truncateAll() {
        var tables = dsl.fetch(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
                        + "AND tablename <> 'flyway_schema_history'");
        for (var record : tables) {
            String table = record.get(0, String.class);
            dsl.execute("TRUNCATE TABLE " + dsl.render(DSL.name(table)) + " RESTART IDENTITY CASCADE");
        }
    }
}
