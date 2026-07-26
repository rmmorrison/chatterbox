package ca.ryanmorrison.chatterbox.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the location-resolution logic, which is the part that silently
 * mis-migrates rather than failing loudly: a wrong dialect subfolder or a
 * missing {@code classpath:} prefix resolves to nothing, and Flyway reports
 * zero migrations applied rather than an error.
 */
class MigrationsTest {

    private Path dbFile;
    private HikariDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("chatterbox-migrations-test", ".db");
        Files.delete(dbFile);
        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(hc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    private boolean tableExists(String name) throws Exception {
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement(
                     "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            st.setString(1, name);
            try (var rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Test
    void resolvesTheDialectSubfolderAndAppliesMigrations() throws Exception {
        Migrations.run(dataSource, List.of("db/migration/shout"), SQLDialect.SQLITE);

        assertTrue(tableExists("shouts"), "expected the sqlite variant to be applied");
        assertTrue(tableExists("shout_history"));
    }

    @Test
    void acceptsLocationsThatAlreadyCarryTheClasspathPrefix() throws Exception {
        // Bootstrap passes one of each: module-declared locations are bare,
        // but the runtime-config location is written with the prefix.
        Migrations.run(dataSource, List.of("classpath:db/migration/runtime-config"),
                SQLDialect.SQLITE);

        assertTrue(tableExists("runtime_config"));
    }

    @Test
    void appliesEveryModuleLocationIntoOneHistory() throws Exception {
        Migrations.run(dataSource,
                List.of("db/migration/shout", "classpath:db/migration/runtime-config",
                        "db/migration/rss"),
                SQLDialect.SQLITE);

        assertTrue(tableExists("shouts"));
        assertTrue(tableExists("runtime_config"));
        assertTrue(tableExists("rss_feeds"));
        // All modules deliberately share a single history table, which is why
        // migration versions are timestamps.
        assertTrue(tableExists("flyway_schema_history"));
    }

    @Test
    void doesNothingWhenNoLocationsAreRegistered() throws Exception {
        assertDoesNotThrow(() -> Migrations.run(dataSource, List.of(), SQLDialect.SQLITE));
        assertEquals(false, tableExists("flyway_schema_history"));
    }

    @Test
    void rejectsADialectWithNoMigrationSubfolder() {
        // Better to fail loudly than to resolve to a folder that doesn't exist
        // and quietly apply nothing.
        var e = assertThrows(IllegalArgumentException.class,
                () -> Migrations.run(dataSource, List.of("db/migration/shout"), SQLDialect.MYSQL));
        assertTrue(e.getMessage().contains("MYSQL"));
    }

    @Test
    void isIdempotentAcrossRuns() throws Exception {
        Migrations.run(dataSource, List.of("db/migration/shout"), SQLDialect.SQLITE);
        assertDoesNotThrow(
                () -> Migrations.run(dataSource, List.of("db/migration/shout"), SQLDialect.SQLITE));
        assertTrue(tableExists("shouts"));
    }
}
