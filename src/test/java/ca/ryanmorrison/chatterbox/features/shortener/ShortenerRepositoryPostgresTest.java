package ca.ryanmorrison.chatterbox.features.shortener;

import ca.ryanmorrison.chatterbox.db.PostgresRepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dialect-parity coverage for {@link ShortenerRepository}, previously verified
 * against SQLite only. Exercises the two constructs whose rendered SQL differs
 * by dialect: {@code returning().fetchOne()} on insert and the
 * {@code CLICK_COUNT.plus(1)} atomic increment.
 */
class ShortenerRepositoryPostgresTest extends PostgresRepositoryTestBase {

    private static final long USER = 4242L;
    private static final long MOD = 9999L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 9, 20, 0, 0, 0, ZoneOffset.UTC);

    private ShortenerRepository repo;

    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/shortener/postgresql";
    }

    @BeforeEach
    void createRepository() {
        repo = new ShortenerRepository(dsl);
    }

    /** insert(...).returning().fetchOne() — the generated-key path. */
    @Test
    void insertReturnsTheStoredRow() {
        var created = repo.insert("abc123", "https://example.com/x", USER, NOW).orElseThrow();

        assertEquals("abc123", created.token());
        assertEquals("https://example.com/x", created.url());
        assertTrue(created.id() > 0, "expected a generated id");
    }

    @Test
    void findByTokenReturnsLiveRowsOnly() {
        long id = repo.insert("abc123", "https://example.com/x", USER, NOW).orElseThrow().id();
        assertTrue(repo.findByToken("abc123").isPresent());

        repo.softDelete(id, MOD, NOW);

        assertTrue(repo.findByToken("abc123").isEmpty());
        assertTrue(repo.findByTokenIncludingDeleted("abc123").isPresent());
    }

    @Test
    void duplicateTokenIsRejected() {
        repo.insert("abc123", "https://example.com/x", USER, NOW).orElseThrow();
        assertTrue(repo.insert("abc123", "https://example.com/y", USER, NOW).isEmpty(),
                "the unique constraint should surface as an empty Optional");
    }

    /** CLICK_COUNT.plus(1) — the atomic in-place increment. */
    @Test
    void incrementClicksAccumulates() {
        long id = repo.insert("abc123", "https://example.com/x", USER, NOW).orElseThrow().id();

        repo.incrementClicks(id, NOW);
        repo.incrementClicks(id, NOW.plusMinutes(1));
        repo.incrementClicks(id, NOW.plusMinutes(2));

        var after = repo.findByIdIncludingDeleted(id).orElseThrow();
        assertEquals(3L, after.clickCount());
        assertEquals(NOW.plusMinutes(2), after.lastClickedAt().orElseThrow());
    }

    @Test
    void softDeleteIsIdempotent() {
        long id = repo.insert("abc123", "https://example.com/x", USER, NOW).orElseThrow().id();

        assertEquals(1, repo.softDelete(id, MOD, NOW));
        assertEquals(0, repo.softDelete(id, 1234L, NOW.plusHours(1)),
                "a second delete must not overwrite the original deleter");
    }

    @Test
    void findByUrlLocatesAnExistingLiveShortening() {
        repo.insert("abc123", "https://example.com/x", USER, NOW).orElseThrow();
        assertEquals("abc123", repo.findByUrl("https://example.com/x").orElseThrow().token());
    }
}
