package ca.ryanmorrison.chatterbox.features.shout;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUTS;
import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUT_HISTORY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoutHistoryRepositoryTest {

    private static final OffsetDateTime AUTHORED_AT =
            OffsetDateTime.of(2026, 4, 30, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final long AUTHOR = 7777L;
    private static final long MODERATOR = 9999L;
    private static final boolean MOD = true;
    private static final boolean NON_MOD = false;

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;
    private static DSLContext dsl;
    private ShoutRepository shouts;
    private ShoutHistoryRepository history;

    @BeforeAll
    static void startContainer() {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("chatterbox_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        var hc = new HikariConfig();
        hc.setJdbcUrl(postgres.getJdbcUrl());
        hc.setUsername(postgres.getUsername());
        hc.setPassword(postgres.getPassword());
        dataSource = new HikariDataSource(hc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/shout/postgresql")
                .load()
                .migrate();

        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    @AfterAll
    static void stopContainer() {
        if (dataSource != null) dataSource.close();
        if (postgres != null) postgres.stop();
    }

    @BeforeEach
    void truncate() {
        dsl.truncate(SHOUTS).restartIdentity().cascade().execute();
        dsl.truncate(SHOUT_HISTORY).restartIdentity().execute();
        shouts = new ShoutRepository(dsl);
        history = new ShoutHistoryRepository(dsl);
    }

    /** Stores a shout and returns the generated id. */
    private long shout(long channelId, long messageId, String content) {
        shouts.tryInsert(channelId, messageId, content, AUTHOR, AUTHORED_AT);
        return dsl.select(SHOUTS.ID).from(SHOUTS).where(SHOUTS.MESSAGE_ID.eq(messageId)).fetchOne(SHOUTS.ID);
    }

    @Test
    void recordAndFindLatest() {
        long s1 = shout(1L, 100L, "FIRST SHOUT IN CHANNEL");
        history.record(1L, s1);
        Optional<HistoryEntry> latest = history.findLatest(1L, NON_MOD);
        assertTrue(latest.isPresent());
        assertEquals("FIRST SHOUT IN CHANNEL", latest.get().content());
        assertEquals(s1, latest.get().shoutId());
        assertEquals(AUTHOR, latest.get().authorId());
        assertTrue(latest.get().deletion().isEmpty());
    }

    @Test
    void findLatestIsEmptyWhenNoHistory() {
        assertFalse(history.findLatest(1L, NON_MOD).isPresent());
        assertFalse(history.findLatest(1L, MOD).isPresent());
    }

    @Test
    void findLatestIsScopedToChannel() {
        long c1 = shout(1L, 100L, "CHANNEL ONE SHOUT");
        long c2 = shout(2L, 200L, "CHANNEL TWO SHOUT");
        history.record(1L, c1);
        history.record(2L, c2);
        assertEquals("CHANNEL ONE SHOUT", history.findLatest(1L, NON_MOD).orElseThrow().content());
        assertEquals("CHANNEL TWO SHOUT", history.findLatest(2L, NON_MOD).orElseThrow().content());
    }

    @Test
    void paginateOlderAndNewer() {
        long s1 = shout(1L, 100L, "FIRST SHOUT IN HISTORY");
        long s2 = shout(1L, 101L, "SECOND SHOUT IN HISTORY");
        long s3 = shout(1L, 102L, "THIRD SHOUT IN HISTORY");
        history.record(1L, s1);
        history.record(1L, s2);
        history.record(1L, s3);

        HistoryEntry latest = history.findLatest(1L, NON_MOD).orElseThrow();
        assertEquals("THIRD SHOUT IN HISTORY", latest.content());

        HistoryEntry older = history.findOlder(1L, latest.historyId(), NON_MOD).orElseThrow();
        assertEquals("SECOND SHOUT IN HISTORY", older.content());

        HistoryEntry oldest = history.findOlder(1L, older.historyId(), NON_MOD).orElseThrow();
        assertEquals("FIRST SHOUT IN HISTORY", oldest.content());

        assertFalse(history.findOlder(1L, oldest.historyId(), NON_MOD).isPresent());

        HistoryEntry backNewer = history.findNewer(1L, oldest.historyId(), NON_MOD).orElseThrow();
        assertEquals("SECOND SHOUT IN HISTORY", backNewer.content());

        assertFalse(history.findNewer(1L, latest.historyId(), NON_MOD).isPresent());
    }

    @Test
    void positionAtNewestIsRankOne() {
        long s1 = shout(1L, 100L, "FIRST SHOUT IN HISTORY");
        long s2 = shout(1L, 101L, "SECOND SHOUT IN HISTORY");
        history.record(1L, s1);
        history.record(1L, s2);
        HistoryEntry latest = history.findLatest(1L, NON_MOD).orElseThrow();
        var pos = history.position(1L, latest.historyId(), NON_MOD);
        assertEquals(1, pos.rank());
        assertEquals(2, pos.total());
    }

    @Test
    void positionWalksTowardsOldest() {
        long s1 = shout(1L, 100L, "FIRST SHOUT IN HISTORY");
        long s2 = shout(1L, 101L, "SECOND SHOUT IN HISTORY");
        long s3 = shout(1L, 102L, "THIRD SHOUT IN HISTORY");
        history.record(1L, s1);
        history.record(1L, s2);
        history.record(1L, s3);

        HistoryEntry latest = history.findLatest(1L, NON_MOD).orElseThrow();
        HistoryEntry middle = history.findOlder(1L, latest.historyId(), NON_MOD).orElseThrow();
        HistoryEntry oldest = history.findOlder(1L, middle.historyId(), NON_MOD).orElseThrow();

        assertEquals(2, history.position(1L, middle.historyId(), NON_MOD).rank());
        assertEquals(3, history.position(1L, oldest.historyId(), NON_MOD).rank());
    }

    @Test
    void deletingShoutCascadesToHistory() {
        long s1 = shout(1L, 100L, "DOOMED SHOUT FOREVER");
        long s2 = shout(1L, 101L, "SURVIVING SHOUT FOREVER");
        history.record(1L, s1);
        history.record(1L, s2);
        assertEquals(2, dsl.fetchCount(SHOUT_HISTORY));

        shouts.deleteByMessageId(100L);
        assertEquals(1, dsl.fetchCount(SHOUT_HISTORY));
        assertEquals("SURVIVING SHOUT FOREVER", history.findLatest(1L, NON_MOD).orElseThrow().content());
    }

    @Test
    void editCollisionCascadesHistoryForOriginalRow() {
        long s1 = shout(1L, 100L, "ORIGINAL CONTENT HERE");
        long s2 = shout(1L, 101L, "EXISTING TWIN CONTENT");
        history.record(1L, s1);
        history.record(1L, s1);
        history.record(1L, s2);
        assertEquals(3, dsl.fetchCount(SHOUT_HISTORY));

        shouts.updateOrDeleteOnCollision(1L, 100L, "EXISTING TWIN CONTENT");
        assertEquals(1, dsl.fetchCount(SHOUT_HISTORY));
        assertEquals("EXISTING TWIN CONTENT", history.findLatest(1L, NON_MOD).orElseThrow().content());
    }

    @Test
    void softDeletedShoutHiddenFromNonModerator() {
        long s1 = shout(1L, 100L, "VISIBLE TO EVERYONE");
        long s2 = shout(1L, 101L, "HIDDEN AFTER DELETE");
        history.record(1L, s1);
        history.record(1L, s2);

        shouts.softDelete(s2, MODERATOR);

        // Non-moderator: only sees s1 in history.
        assertEquals("VISIBLE TO EVERYONE", history.findLatest(1L, NON_MOD).orElseThrow().content());
        assertEquals(1, history.position(1L,
                history.findLatest(1L, NON_MOD).orElseThrow().historyId(), NON_MOD).total());
    }

    @Test
    void softDeletedShoutVisibleToModeratorWithDeletionMetadata() {
        long s = shout(1L, 100L, "FLAGGED FOR REVIEW");
        history.record(1L, s);
        shouts.softDelete(s, MODERATOR);

        HistoryEntry entry = history.findLatest(1L, MOD).orElseThrow();
        assertTrue(entry.deletion().isPresent());
        assertEquals(MODERATOR, entry.deletion().get().deletedBy());
    }

    @Test
    void positionCountsDifferBetweenViewers() {
        long s1 = shout(1L, 100L, "ENTRY ONE HERE");
        long s2 = shout(1L, 101L, "ENTRY TWO HERE");
        long s3 = shout(1L, 102L, "ENTRY THREE HERE");
        history.record(1L, s1);
        history.record(1L, s2);
        history.record(1L, s3);

        shouts.softDelete(s2, MODERATOR);

        // Moderator: still sees three entries; latest is s3 (rank 1 of 3).
        HistoryEntry modLatest = history.findLatest(1L, MOD).orElseThrow();
        assertEquals("ENTRY THREE HERE", modLatest.content());
        assertEquals(3, history.position(1L, modLatest.historyId(), MOD).total());

        // Non-moderator: sees two entries; latest is s3 (rank 1 of 2).
        HistoryEntry userLatest = history.findLatest(1L, NON_MOD).orElseThrow();
        assertEquals("ENTRY THREE HERE", userLatest.content());
        assertEquals(2, history.position(1L, userLatest.historyId(), NON_MOD).total());

        // Non-moderator stepping older from latest skips the deleted middle entry,
        // arriving directly at s1.
        HistoryEntry skipped = history.findOlder(1L, userLatest.historyId(), NON_MOD).orElseThrow();
        assertEquals("ENTRY ONE HERE", skipped.content());
    }

    @Test
    void softDeletedShoutExcludedFromRandomPeer() {
        long s1 = shout(1L, 100L, "ALWAYS RETURNED HERE");
        long s2 = shout(1L, 101L, "EVENTUALLY DELETED HERE");
        shouts.softDelete(s2, MODERATOR);

        // Excluding messageId 100 leaves only s2, which is soft-deleted —
        // randomPeer must skip it.
        assertTrue(shouts.randomPeer(1L, 100L).isEmpty());
        // Excluding 999 (no match), only s1 is eligible.
        assertEquals("ALWAYS RETURNED HERE", shouts.randomPeer(1L, 999L).orElseThrow().content());
    }

    @Test
    void restoreClearsBothFlagAndDeleter() {
        long s = shout(1L, 100L, "TEMPORARILY GONE HERE");
        history.record(1L, s);
        shouts.softDelete(s, MODERATOR);
        shouts.restore(s);

        HistoryEntry entry = history.findLatest(1L, NON_MOD).orElseThrow();
        assertTrue(entry.deletion().isEmpty());
        // randomPeer also sees it again.
        assertEquals("TEMPORARILY GONE HERE", shouts.randomPeer(1L, 999L).orElseThrow().content());
    }

    @Test
    void softDeleteIsIdempotentOnSecondClick() {
        long s = shout(1L, 100L, "ORIGINAL DELETE TARGET HERE");
        history.record(1L, s);
        shouts.softDelete(s, MODERATOR);
        OffsetDateTime firstDeletedAt = history.findLatest(1L, MOD).orElseThrow().deletion().orElseThrow().deletedAt();

        // A second moderator clicking delete must not overwrite the original deleter/timestamp.
        shouts.softDelete(s, 1234L);
        HistoryEntry entry = history.findLatest(1L, MOD).orElseThrow();
        assertEquals(MODERATOR, entry.deletion().orElseThrow().deletedBy());
        assertEquals(firstDeletedAt, entry.deletion().orElseThrow().deletedAt());
    }
}
