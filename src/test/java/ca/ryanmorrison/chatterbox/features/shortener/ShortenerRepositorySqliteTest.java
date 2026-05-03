package ca.ryanmorrison.chatterbox.features.shortener;

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the SQLite migrations are wire-compatible with the generated jOOQ classes. */
class ShortenerRepositorySqliteTest {

    private static final long USER = 4242L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 3, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OpenGraphMetadata META = new OpenGraphMetadata(
            "Title", "Desc", "https://img/x.png", "Site");

    private Path dbFile;
    private HikariDataSource dataSource;
    private DSLContext dsl;
    private ShortenerRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("chatterbox-shortener-test", ".db");
        Files.delete(dbFile);

        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
        hc.setConnectionInitSql("PRAGMA foreign_keys = ON");
        dataSource = new HikariDataSource(hc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/shortener/sqlite")
                .load()
                .migrate();

        dsl = DSL.using(dataSource, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
        repo = new ShortenerRepository(dsl);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    @Test
    void insertAndLookupByToken() {
        Optional<ShortenedUrl> created = repo.insert("abc123", "https://example.com/x", USER, NOW, META);
        assertTrue(created.isPresent());
        ShortenedUrl found = repo.findByToken("abc123").orElseThrow();
        assertEquals("https://example.com/x", found.url());
        assertEquals(USER, found.createdBy());
        assertEquals("Title", found.metadata().title());
        assertEquals("Site", found.metadata().siteName());
    }

    @Test
    void emptyMetadataRoundTripsAsNullColumns() {
        repo.insert("blank1", "https://example.com/blank", USER, NOW, OpenGraphMetadata.EMPTY);
        ShortenedUrl found = repo.findByToken("blank1").orElseThrow();
        assertNull(found.metadata().title());
        assertNull(found.metadata().description());
        assertNull(found.metadata().image());
        assertNull(found.metadata().siteName());
    }

    @Test
    void duplicateTokenInsertReturnsEmpty() {
        repo.insert("abc123", "https://example.com/x", USER, NOW, OpenGraphMetadata.EMPTY);
        Optional<ShortenedUrl> dup = repo.insert("abc123", "https://example.com/y", USER, NOW, OpenGraphMetadata.EMPTY);
        assertTrue(dup.isEmpty());
    }

    @Test
    void duplicateUrlInsertReturnsEmpty() {
        repo.insert("aaaaaa", "https://example.com/x", USER, NOW, OpenGraphMetadata.EMPTY);
        Optional<ShortenedUrl> dup = repo.insert("bbbbbb", "https://example.com/x", USER, NOW, OpenGraphMetadata.EMPTY);
        assertTrue(dup.isEmpty());
    }

    @Test
    void findByUrlReturnsExisting() {
        repo.insert("abc123", "https://example.com/x", USER, NOW, OpenGraphMetadata.EMPTY);
        ShortenedUrl found = repo.findByUrl("https://example.com/x").orElseThrow();
        assertEquals("abc123", found.token());
    }

    @Test
    void findByMissingTokenIsEmpty() {
        assertTrue(repo.findByToken("nope12").isEmpty());
    }
}
