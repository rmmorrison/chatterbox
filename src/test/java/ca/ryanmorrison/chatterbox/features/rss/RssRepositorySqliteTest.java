package ca.ryanmorrison.chatterbox.features.rss;

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
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.RSS_FEEDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the SQLite migration is wire-compatible with the generated jOOQ classes. */
class RssRepositorySqliteTest {

    private static final long GUILD   = 100L;
    private static final long CHANNEL = 1L;
    private static final long USER    = 7777L;

    private Path dbFile;
    private HikariDataSource dataSource;
    private DSLContext dsl;
    private RssRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("chatterbox-rss-test", ".db");
        Files.delete(dbFile);

        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
        hc.setConnectionInitSql("PRAGMA foreign_keys = ON");
        dataSource = new HikariDataSource(hc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/rss/sqlite")
                .load()
                .migrate();

        dsl = DSL.using(dataSource, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
        repo = new RssRepository(dsl);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    @Test
    void insertAndFetchAgainstSqlite() {
        Optional<Feed> created = repo.insert(GUILD, CHANNEL, "https://x/feed", "X Feed", USER, 60);
        assertTrue(created.isPresent());
        Feed found = repo.findById(created.get().id()).orElseThrow();
        assertEquals("X Feed", found.title());
        assertEquals(60, found.refreshMinutes());
    }

    @Test
    void duplicateUrlIgnoredAgainstSqlite() {
        repo.insert(GUILD, CHANNEL, "https://x/feed", "X", USER, 60);
        Optional<Feed> dup = repo.insert(GUILD, CHANNEL, "https://x/feed", "X", USER, 60);
        assertTrue(dup.isEmpty());
        assertEquals(1, dsl.fetchCount(RSS_FEEDS));
    }
}
