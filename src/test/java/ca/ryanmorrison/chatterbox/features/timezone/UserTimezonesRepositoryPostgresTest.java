package ca.ryanmorrison.chatterbox.features.timezone;

import ca.ryanmorrison.chatterbox.db.PostgresRepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dialect-parity coverage for {@link UserTimezonesRepository}, previously
 * verified against SQLite only. Like the runtime-config repository it upserts
 * via {@code onConflict().doUpdate()}.
 */
class UserTimezonesRepositoryPostgresTest extends PostgresRepositoryTestBase {

    private static final long USER = 4242L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 9, 8, 0, 0, 0, ZoneOffset.UTC);

    private UserTimezonesRepository repo;

    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/user-timezones/postgresql";
    }

    @BeforeEach
    void createRepository() {
        repo = new UserTimezonesRepository(dsl);
    }

    @Test
    void putThenFind() {
        repo.put(USER, "America/Toronto", NOW);
        assertEquals("America/Toronto", repo.find(USER).orElseThrow());
    }

    @Test
    void findIsEmptyForAnUnknownUser() {
        assertTrue(repo.find(USER).isEmpty());
    }

    @Test
    void putUpdatesTheExistingRow() {
        repo.put(USER, "America/Toronto", NOW);
        repo.put(USER, "Europe/Berlin", NOW.plusHours(1));

        assertEquals("Europe/Berlin", repo.find(USER).orElseThrow());
    }

    @Test
    void deleteReportsWhetherARowExisted() {
        repo.put(USER, "America/Toronto", NOW);

        assertTrue(repo.delete(USER));
        assertFalse(repo.delete(USER));
        assertTrue(repo.find(USER).isEmpty());
    }
}
