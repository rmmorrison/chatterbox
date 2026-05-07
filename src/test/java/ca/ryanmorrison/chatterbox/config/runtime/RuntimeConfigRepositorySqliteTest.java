package ca.ryanmorrison.chatterbox.config.runtime;

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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the SQLite migration is wire-compatible with the generated jOOQ classes. */
class RuntimeConfigRepositorySqliteTest {

    private static final long GUILD = 12345L;
    private static final long ADMIN = 9999L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 7, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime LATER =
            OffsetDateTime.of(2026, 5, 7, 13, 0, 0, 0, ZoneOffset.UTC);

    private Path dbFile;
    private HikariDataSource dataSource;
    private DSLContext dsl;
    private RuntimeConfigRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("chatterbox-runtime-config-test", ".db");
        Files.delete(dbFile);

        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(hc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/runtime-config/sqlite")
                .load()
                .migrate();

        dsl = DSL.using(dataSource, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
        repo = new RuntimeConfigRepository(dsl);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    @Test
    void putThenFindReturnsValue() {
        repo.put(GUILD, "autoshorten.enabled", "false", ADMIN, NOW);
        assertEquals("false", repo.findValue(GUILD, "autoshorten.enabled").orElseThrow());
    }

    @Test
    void findValueIsEmptyWhenAbsent() {
        assertTrue(repo.findValue(GUILD, "nope").isEmpty());
    }

    @Test
    void putUpdatesExistingRow() {
        repo.put(GUILD, "autoshorten.threshold", "200", ADMIN, NOW);
        repo.put(GUILD, "autoshorten.threshold", "300", ADMIN, LATER);
        assertEquals("300", repo.findValue(GUILD, "autoshorten.threshold").orElseThrow());
    }

    @Test
    void findAllForGuildReturnsEverythingForOneGuild() {
        repo.put(GUILD, "autoshorten.enabled",   "false", ADMIN, NOW);
        repo.put(GUILD, "autoshorten.threshold", "200",   ADMIN, NOW);
        repo.put(99L,   "autoshorten.threshold", "999",   ADMIN, NOW); // other guild

        Map<String, String> got = repo.findAllForGuild(GUILD);
        assertEquals(2, got.size());
        assertEquals("false", got.get("autoshorten.enabled"));
        assertEquals("200",   got.get("autoshorten.threshold"));
    }

    @Test
    void deleteReturnsTrueWhenRowExisted() {
        repo.put(GUILD, "autoshorten.enabled", "false", ADMIN, NOW);
        assertTrue(repo.delete(GUILD, "autoshorten.enabled"));
        assertTrue(repo.findValue(GUILD, "autoshorten.enabled").isEmpty());
    }

    @Test
    void deleteReturnsFalseWhenRowAbsent() {
        assertFalse(repo.delete(GUILD, "missing"));
    }
}
