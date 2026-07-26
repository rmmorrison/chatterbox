package ca.ryanmorrison.chatterbox.features.timezone;

import ca.ryanmorrison.chatterbox.db.SqliteRepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the SQLite migration is wire-compatible with the generated jOOQ classes. */
class UserTimezonesRepositorySqliteTest extends SqliteRepositoryTestBase {

    private static final long USER = 4242L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 9, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime LATER =
            OffsetDateTime.of(2026, 5, 9, 1, 0, 0, 0, ZoneOffset.UTC);

    private UserTimezonesRepository repo;

    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/user-timezones/sqlite";
    }

    @BeforeEach
    void createRepositories() {
        repo = new UserTimezonesRepository(dsl);
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
