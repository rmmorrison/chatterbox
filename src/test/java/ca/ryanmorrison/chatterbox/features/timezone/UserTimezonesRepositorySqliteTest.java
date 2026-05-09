package ca.ryanmorrison.chatterbox.features.timezone;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the SQLite migration is wire-compatible with the generated jOOQ classes. */
class UserTimezonesRepositorySqliteTest {

    private static final long USER = 4242L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 9, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime LATER =
            OffsetDateTime.of(2026, 5, 9, 1, 0, 0, 0, ZoneOffset.UTC);

    private Path dbFile;
    private HikariDataSource dataSource;
    private DSLContext dsl;
    private UserTimezonesRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("chatterbox-user-tz-test", ".db");
        Files.delete(dbFile);

        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(hc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/user-timezones/sqlite")
                .load()
                .migrate();

        dsl = DSL.using(dataSource, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
        repo = new UserTimezonesRepository(dsl);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    @Test
    void putThenFindRoundTrips() {
        repo.put(USER, "America/Toronto", NOW);
        assertEquals("America/Toronto", repo.find(USER).orElseThrow());
    }

    @Test
    void findIsEmptyForUnknownUser() {
        assertTrue(repo.find(USER).isEmpty());
    }

    @Test
    void putUpdatesExistingRow() {
        repo.put(USER, "America/Toronto", NOW);
        repo.put(USER, "Asia/Kolkata",   LATER);
        assertEquals("Asia/Kolkata", repo.find(USER).orElseThrow());
    }

    @Test
    void deleteReturnsTrueWhenRowExisted() {
        repo.put(USER, "Europe/London", NOW);
        assertTrue(repo.delete(USER));
        assertTrue(repo.find(USER).isEmpty());
    }

    @Test
    void deleteReturnsFalseWhenRowAbsent() {
        assertFalse(repo.delete(USER));
    }
}
