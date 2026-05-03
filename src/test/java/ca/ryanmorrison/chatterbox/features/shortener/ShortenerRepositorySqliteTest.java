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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the SQLite migrations are wire-compatible with the generated jOOQ classes. */
class ShortenerRepositorySqliteTest {

    private static final long USER = 4242L;
    private static final long MOD = 9999L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 3, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime LATER =
            OffsetDateTime.of(2026, 5, 3, 13, 0, 0, 0, ZoneOffset.UTC);

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
        Optional<ShortenedUrl> created = repo.insert("abc123", "https://example.com/x", USER, NOW);
        assertTrue(created.isPresent());
        ShortenedUrl found = repo.findByToken("abc123").orElseThrow();
        assertEquals("https://example.com/x", found.url());
        assertEquals(USER, found.createdBy());
        assertFalse(found.isDeleted());
    }

    @Test
    void duplicateTokenInsertReturnsEmpty() {
        repo.insert("abc123", "https://example.com/x", USER, NOW);
        Optional<ShortenedUrl> dup = repo.insert("abc123", "https://example.com/y", USER, NOW);
        assertTrue(dup.isEmpty());
    }

    @Test
    void duplicateLiveUrlInsertReturnsEmpty() {
        repo.insert("aaaaaa", "https://example.com/x", USER, NOW);
        Optional<ShortenedUrl> dup = repo.insert("bbbbbb", "https://example.com/x", USER, NOW);
        assertTrue(dup.isEmpty());
    }

    @Test
    void findByUrlReturnsExistingLive() {
        repo.insert("abc123", "https://example.com/x", USER, NOW);
        ShortenedUrl found = repo.findByUrl("https://example.com/x").orElseThrow();
        assertEquals("abc123", found.token());
    }

    @Test
    void findByMissingTokenIsEmpty() {
        assertTrue(repo.findByToken("nope12").isEmpty());
    }

    @Test
    void softDeleteHidesFromLiveLookups() {
        long id = repo.insert("abc123", "https://example.com/x", USER, NOW).orElseThrow().id();
        int updated = repo.softDelete(id, MOD, LATER);
        assertEquals(1, updated);

        assertTrue(repo.findByToken("abc123").isEmpty(),
                "deleted token should not surface from live lookup");
        assertTrue(repo.findByUrl("https://example.com/x").isEmpty(),
                "deleted url should not surface from live lookup");

        ShortenedUrl tombstone = repo.findByTokenIncludingDeleted("abc123").orElseThrow();
        assertTrue(tombstone.isDeleted());
        assertEquals(MOD, tombstone.deletedBy().orElseThrow());
        assertEquals(LATER, tombstone.deletedAt().orElseThrow());
    }

    @Test
    void softDeleteIsIdempotent() {
        long id = repo.insert("abc123", "https://example.com/x", USER, NOW).orElseThrow().id();
        repo.softDelete(id, MOD, LATER);
        // Second call: no-op, preserves original deleter and timestamp.
        int updated = repo.softDelete(id, 1234L, NOW);
        assertEquals(0, updated);
        ShortenedUrl tombstone = repo.findByTokenIncludingDeleted("abc123").orElseThrow();
        assertEquals(MOD, tombstone.deletedBy().orElseThrow());
        assertEquals(LATER, tombstone.deletedAt().orElseThrow());
    }

    @Test
    void deletedUrlCanBeReshortenedWithFreshToken() {
        repo.insert("aaaaaa", "https://example.com/x", USER, NOW);
        repo.softDelete(repo.findByToken("aaaaaa").orElseThrow().id(), MOD, LATER);

        Optional<ShortenedUrl> fresh = repo.insert("bbbbbb", "https://example.com/x", USER, LATER);
        assertTrue(fresh.isPresent(), "partial unique index should allow re-insert after delete");
        assertEquals("bbbbbb", fresh.get().token());
        assertNotEquals("aaaaaa", fresh.get().token());
    }

    @Test
    void deletedTokenCannotBeReissued() {
        long id = repo.insert("abc123", "https://example.com/x", USER, NOW).orElseThrow().id();
        repo.softDelete(id, MOD, LATER);
        // Token unique index is unconditional → re-using the same token must fail.
        Optional<ShortenedUrl> reuse = repo.insert("abc123", "https://example.com/y", USER, LATER);
        assertTrue(reuse.isEmpty());
    }

    @Test
    void findByIdIncludingDeletedReturnsTombstone() {
        long id = repo.insert("abc123", "https://example.com/x", USER, NOW).orElseThrow().id();
        repo.softDelete(id, MOD, LATER);
        ShortenedUrl byId = repo.findByIdIncludingDeleted(id).orElseThrow();
        assertTrue(byId.isDeleted());
    }
}
