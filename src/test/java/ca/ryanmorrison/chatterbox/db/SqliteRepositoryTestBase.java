package ca.ryanmorrison.chatterbox.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base for repository tests that run against a file-backed SQLite database.
 *
 * <p>Collapses the Hikari + Flyway + {@code DSL.using} block that was
 * copy-pasted across every {@code *SqliteTest}. The three settings that matter
 * and are easy to get wrong are fixed here: a pool of one (SQLite serialises
 * writes), {@code foreign_keys} on (off per-connection by default, so
 * {@code ON DELETE CASCADE} silently does nothing without it), and
 * {@code withRenderSchema(false)} (the generated classes embed a {@code public}
 * schema that SQLite doesn't have).
 *
 * <p>Subclasses declare which migration folder to apply via
 * {@link #migrationLocation()}.
 */
public abstract class SqliteRepositoryTestBase {

    private Path dbFile;
    private HikariDataSource dataSource;

    protected DSLContext dsl;

    /** Classpath location of this feature's SQLite migrations. */
    protected abstract String migrationLocation();

    @BeforeEach
    void openDatabase() throws Exception {
        dbFile = Files.createTempFile("chatterbox-test", ".db");
        Files.delete(dbFile);

        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
        hc.addDataSourceProperty("foreign_keys", "true");
        dataSource = new HikariDataSource(hc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocation())
                .load()
                .migrate();

        dsl = DSL.using(dataSource, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
    }

    @AfterEach
    void closeDatabase() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) {
            Files.deleteIfExists(dbFile);
            // WAL leaves sidecar files behind.
            Files.deleteIfExists(Path.of(dbFile + "-wal"));
            Files.deleteIfExists(Path.of(dbFile + "-shm"));
        }
    }

    /** The live datasource, for the rare test that needs raw JDBC. */
    protected HikariDataSource dataSource() {
        return dataSource;
    }
}
