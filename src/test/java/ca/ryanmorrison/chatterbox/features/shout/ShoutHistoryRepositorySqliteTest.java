package ca.ryanmorrison.chatterbox.features.shout;

import ca.ryanmorrison.chatterbox.db.SqliteRepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUTS;
import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUT_HISTORY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the SQLite migration includes the FK with cascade and that
 * Hikari's connection-init {@code PRAGMA foreign_keys = ON} actually applies
 * — without it, SQLite silently ignores the constraint.
 */
class ShoutHistoryRepositorySqliteTest extends SqliteRepositoryTestBase {

    private static final OffsetDateTime AUTHORED_AT =
            OffsetDateTime.of(2026, 4, 30, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final long AUTHOR = 7777L;
    private static final long MODERATOR = 9999L;
    private static final boolean MOD = true;
    private static final boolean NON_MOD = false;

    private ShoutRepository shouts;
    private ShoutHistoryRepository history;

    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/shout/sqlite";
    }

    @BeforeEach
    void createRepositories() {
        shouts = new ShoutRepository(dsl);
        history = new ShoutHistoryRepository(dsl);
    }


    private long shout(long channelId, long messageId, String content) {
        shouts.tryInsert(channelId, messageId, content, AUTHOR, AUTHORED_AT);
        return dsl.select(SHOUTS.ID).from(SHOUTS).where(SHOUTS.MESSAGE_ID.eq(messageId)).fetchOne(SHOUTS.ID);
    }

    @Test
    void recordAndFindLatestAgainstSqlite() {
        long s1 = shout(1L, 100L, "HELLO WORLD AGAIN");
        history.record(1L, s1);
        assertTrue(history.findLatest(1L, NON_MOD).isPresent());
        assertEquals("HELLO WORLD AGAIN", history.findLatest(1L, NON_MOD).orElseThrow().content());
    }

    @Test
    void deleteCascadesAgainstSqlite() {
        long s1 = shout(1L, 100L, "DOOMED SHOUT FOREVER");
        history.record(1L, s1);
        assertEquals(1, dsl.fetchCount(SHOUT_HISTORY));
        shouts.deleteByMessageId(100L);
        assertEquals(0, dsl.fetchCount(SHOUT_HISTORY),
                "FK cascade should remove history rows when the shout is deleted");
    }

    @Test
    void softDeleteHidesFromNonModeratorAndExposesToModeratorAgainstSqlite() {
        long s = shout(1L, 100L, "FLAGGED SHOUT HERE");
        history.record(1L, s);
        shouts.softDelete(s, MODERATOR);

        assertFalse(history.findLatest(1L, NON_MOD).isPresent(),
                "non-moderator should not see deleted entries");
        HistoryEntry modView = history.findLatest(1L, MOD).orElseThrow();
        assertTrue(modView.deletion().isPresent());
        assertEquals(MODERATOR, modView.deletion().orElseThrow().deletedBy());
    }

    @Test
    void restoreClearsFlagAgainstSqlite() {
        long s = shout(1L, 100L, "TEMPORARILY HIDDEN SHOUT");
        history.record(1L, s);
        shouts.softDelete(s, MODERATOR);
        shouts.restore(s);

        HistoryEntry entry = history.findLatest(1L, NON_MOD).orElseThrow();
        assertTrue(entry.deletion().isEmpty(),
                "deletion metadata should clear after restore");
    }
}
