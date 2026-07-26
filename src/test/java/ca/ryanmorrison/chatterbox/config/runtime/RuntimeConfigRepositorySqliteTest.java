package ca.ryanmorrison.chatterbox.config.runtime;

import ca.ryanmorrison.chatterbox.db.SqliteRepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the SQLite migration is wire-compatible with the generated jOOQ classes. */
class RuntimeConfigRepositorySqliteTest extends SqliteRepositoryTestBase {

    private static final long GUILD = 12345L;
    private static final long ADMIN = 9999L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 7, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime LATER =
            OffsetDateTime.of(2026, 5, 7, 13, 0, 0, 0, ZoneOffset.UTC);

    private RuntimeConfigRepository repo;

    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/runtime-config/sqlite";
    }

    @BeforeEach
    void createRepositories() {
        repo = new RuntimeConfigRepository(dsl);
    }


    @Test
    void putThenFindReturnsValue() {
        repo.put(GUILD, "autoshorten.enabled", "false", ADMIN, NOW);
        assertEquals("false", repo.findAllForGuild(GUILD).get("autoshorten.enabled"));
    }

    @Test
    void lookupIsEmptyWhenAbsent() {
        assertNull(repo.findAllForGuild(GUILD).get("nope"));
    }

    @Test
    void putUpdatesExistingRow() {
        repo.put(GUILD, "autoshorten.threshold", "200", ADMIN, NOW);
        repo.put(GUILD, "autoshorten.threshold", "300", ADMIN, LATER);
        assertEquals("300", repo.findAllForGuild(GUILD).get("autoshorten.threshold"));
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
        assertNull(repo.findAllForGuild(GUILD).get("autoshorten.enabled"));
    }

    @Test
    void deleteReturnsFalseWhenRowAbsent() {
        assertFalse(repo.delete(GUILD, "missing"));
    }
}
