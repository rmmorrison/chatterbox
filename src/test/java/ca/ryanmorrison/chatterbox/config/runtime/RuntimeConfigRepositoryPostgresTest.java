package ca.ryanmorrison.chatterbox.config.runtime;

import ca.ryanmorrison.chatterbox.db.PostgresRepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dialect-parity coverage for {@link RuntimeConfigRepository}, previously
 * verified against SQLite only. The upsert goes through
 * {@code onConflict().doUpdate()}, whose rendered SQL differs by dialect.
 */
class RuntimeConfigRepositoryPostgresTest extends PostgresRepositoryTestBase {

    private static final long GUILD = 12345L;
    private static final long ADMIN = 9999L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 7, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime LATER = NOW.plusHours(1);

    private RuntimeConfigRepository repo;

    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/runtime-config/postgresql";
    }

    @BeforeEach
    void createRepository() {
        repo = new RuntimeConfigRepository(dsl);
    }

    @Test
    void putThenReadBack() {
        repo.put(GUILD, "autoshorten.enabled", "false", ADMIN, NOW);
        assertEquals("false", repo.findAllForGuild(GUILD).get("autoshorten.enabled"));
    }

    @Test
    void lookupIsEmptyWhenAbsent() {
        assertNull(repo.findAllForGuild(GUILD).get("nope"));
    }

    /** The upsert: a second put for the same key must update, not duplicate. */
    @Test
    void putUpdatesExistingRow() {
        repo.put(GUILD, "autoshorten.threshold", "200", ADMIN, NOW);
        repo.put(GUILD, "autoshorten.threshold", "300", ADMIN, LATER);

        assertEquals("300", repo.findAllForGuild(GUILD).get("autoshorten.threshold"));
        assertEquals(1, repo.findAllForGuild(GUILD).size());
    }

    @Test
    void overridesAreScopedPerGuild() {
        repo.put(GUILD, "autoshorten.threshold", "200", ADMIN, NOW);
        repo.put(99L, "autoshorten.threshold", "999", ADMIN, NOW);

        assertEquals("200", repo.findAllForGuild(GUILD).get("autoshorten.threshold"));
        assertEquals("999", repo.findAllForGuild(99L).get("autoshorten.threshold"));
    }

    @Test
    void deleteReportsWhetherARowExisted() {
        repo.put(GUILD, "autoshorten.enabled", "false", ADMIN, NOW);

        assertTrue(repo.delete(GUILD, "autoshorten.enabled"));
        assertFalse(repo.delete(GUILD, "autoshorten.enabled"));
        assertNull(repo.findAllForGuild(GUILD).get("autoshorten.enabled"));
    }
}
