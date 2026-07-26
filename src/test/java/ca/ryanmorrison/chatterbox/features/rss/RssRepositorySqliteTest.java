package ca.ryanmorrison.chatterbox.features.rss;

import ca.ryanmorrison.chatterbox.db.SqliteRepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.RSS_FEEDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the SQLite migration is wire-compatible with the generated jOOQ classes. */
class RssRepositorySqliteTest extends SqliteRepositoryTestBase {

    private static final long GUILD   = 100L;
    private static final long CHANNEL = 1L;
    private static final long USER    = 7777L;

    private RssRepository repo;

    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/rss/sqlite";
    }

    @BeforeEach
    void createRepositories() {
        repo = new RssRepository(dsl);
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
