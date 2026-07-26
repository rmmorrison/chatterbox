package ca.ryanmorrison.chatterbox.features.shout;

import ca.ryanmorrison.chatterbox.db.SqliteRepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity check that the SQLite migration matches the Postgres one closely
 * enough that {@link ShoutRepository} works against either dialect using the
 * Postgres-generated jOOQ classes.
 */
class ShoutRepositorySqliteTest extends SqliteRepositoryTestBase {

    private static final OffsetDateTime AUTHORED_AT =
            OffsetDateTime.of(2026, 4, 30, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final long AUTHOR = 7777L;

    private ShoutRepository repo;

    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/shout/sqlite";
    }

    @BeforeEach
    void createRepositories() {
        repo = new ShoutRepository(dsl);
    }


    @Test
    void insertAndFetchAgainstSqlite() {
        repo.tryInsert(1L, 100L, "HELLO WORLD", AUTHOR, AUTHORED_AT);
        assertEquals(Optional.of("HELLO WORLD"), repo.findContentByMessageId(100L));
    }

    @Test
    void duplicateInsertIsIgnoredAgainstSqlite() {
        repo.tryInsert(1L, 100L, "HELLO WORLD", AUTHOR, AUTHORED_AT);
        repo.tryInsert(1L, 101L, "HELLO WORLD", AUTHOR, AUTHORED_AT);
        assertEquals(1, dsl.fetchCount(SHOUTS));
    }

    @Test
    void updateCollisionDeletesOriginalAgainstSqlite() {
        repo.tryInsert(1L, 100L, "ORIGINAL CONTENT HERE", AUTHOR, AUTHORED_AT);
        repo.tryInsert(1L, 101L, "EXISTING TWIN HERE", AUTHOR, AUTHORED_AT);
        repo.updateOrDeleteOnCollision(1L, 100L, "EXISTING TWIN HERE");
        assertTrue(repo.findContentByMessageId(100L).isEmpty());
        assertEquals(Optional.of("EXISTING TWIN HERE"), repo.findContentByMessageId(101L));
    }
}
