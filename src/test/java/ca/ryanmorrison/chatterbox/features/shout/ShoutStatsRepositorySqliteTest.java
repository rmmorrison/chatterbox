package ca.ryanmorrison.chatterbox.features.shout;

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
import java.util.List;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end SQLite coverage for {@link ShoutStatsRepository}: confirms each
 * query both compiles against the generated jOOQ classes and produces the
 * expected aggregate against a real schema.
 */
class ShoutStatsRepositorySqliteTest {

    private static final long CHANNEL = 1L;
    private static final long OTHER_CHANNEL = 2L;
    private static final long MOD = 9999L;
    private static final OffsetDateTime BASE =
            OffsetDateTime.of(2026, 5, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    private Path dbFile;
    private HikariDataSource dataSource;
    private DSLContext dsl;
    private ShoutRepository shouts;
    private ShoutHistoryRepository history;
    private ShoutStatsRepository stats;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("chatterbox-stats-test", ".db");
        Files.delete(dbFile);

        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
        hc.setConnectionInitSql("PRAGMA foreign_keys = ON");
        dataSource = new HikariDataSource(hc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/shout/sqlite")
                .load()
                .migrate();

        dsl = DSL.using(dataSource, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
        shouts = new ShoutRepository(dsl);
        history = new ShoutHistoryRepository(dsl);
        stats = new ShoutStatsRepository(dsl);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    @Test
    void emptyChannelHasZeroes() {
        assertEquals(0, stats.countLive(CHANNEL));
        assertEquals(0, stats.countDistinctShouters(CHANNEL));
        assertTrue(stats.oldest(CHANNEL).isEmpty());
        assertTrue(stats.newest(CHANNEL).isEmpty());
        assertTrue(stats.longest(CHANNEL).isEmpty());
        assertTrue(stats.mostReplayed(CHANNEL).isEmpty());
        assertTrue(stats.topShouters(CHANNEL, 3).isEmpty());
    }

    @Test
    void countsLiveAndExcludesDeletedAndOtherChannels() {
        long live1 = shout(CHANNEL, 100L, "ALPHA", 1L, BASE);
        long live2 = shout(CHANNEL, 101L, "BETA",  2L, BASE.plusMinutes(1));
        long del   = shout(CHANNEL, 102L, "GAMMA", 3L, BASE.plusMinutes(2));
        shouts.softDelete(del, MOD);
        shout(OTHER_CHANNEL, 200L, "DELTA", 4L, BASE);

        assertEquals(2, stats.countLive(CHANNEL));
        assertEquals(2, stats.countDistinctShouters(CHANNEL),
                "two distinct authors have live shouts; the third author's only shout was deleted; "
                + "the fourth shouts in another channel");
        assertNotEquals(live1, live2);
    }

    @Test
    void distinctShoutersCountsUniqueAuthors() {
        shout(CHANNEL, 100L, "A", 1L, BASE);
        shout(CHANNEL, 101L, "B", 1L, BASE.plusMinutes(1));
        shout(CHANNEL, 102L, "C", 2L, BASE.plusMinutes(2));
        shout(CHANNEL, 103L, "D", 3L, BASE.plusMinutes(3));
        assertEquals(3, stats.countDistinctShouters(CHANNEL));
    }

    @Test
    void countLiveSinceFiltersByAuthoredAt() {
        shout(CHANNEL, 100L, "OLD",    1L, BASE.minusDays(30));
        shout(CHANNEL, 101L, "MEDIUM", 1L, BASE.minusDays(8));
        shout(CHANNEL, 102L, "RECENT", 1L, BASE.minusDays(2));
        shout(CHANNEL, 103L, "FRESH",  1L, BASE.minusHours(1));
        assertEquals(2, stats.countLiveSince(CHANNEL, BASE.minusDays(7)));
    }

    @Test
    void topShoutersOrdersByCountDesc() {
        // user 1 → 3 shouts; user 2 → 2; user 3 → 1
        shout(CHANNEL, 100L, "A1", 1L, BASE);
        shout(CHANNEL, 101L, "A2", 1L, BASE.plusMinutes(1));
        shout(CHANNEL, 102L, "A3", 1L, BASE.plusMinutes(2));
        shout(CHANNEL, 103L, "B1", 2L, BASE.plusMinutes(3));
        shout(CHANNEL, 104L, "B2", 2L, BASE.plusMinutes(4));
        shout(CHANNEL, 105L, "C1", 3L, BASE.plusMinutes(5));

        List<ShoutStats.ShouterCount> top = stats.topShouters(CHANNEL, 3);
        assertEquals(3, top.size());
        assertEquals(1L, top.get(0).userId()); assertEquals(3, top.get(0).count());
        assertEquals(2L, top.get(1).userId()); assertEquals(2, top.get(1).count());
        assertEquals(3L, top.get(2).userId()); assertEquals(1, top.get(2).count());
    }

    @Test
    void topShoutersHonoursLimit() {
        shout(CHANNEL, 100L, "A1", 1L, BASE);
        shout(CHANNEL, 101L, "B1", 2L, BASE.plusMinutes(1));
        shout(CHANNEL, 102L, "C1", 3L, BASE.plusMinutes(2));
        assertEquals(2, stats.topShouters(CHANNEL, 2).size());
    }

    @Test
    void topShoutersIgnoresDeleted() {
        shout(CHANNEL, 100L, "A1", 1L, BASE);
        long deletedFromUser2 = shout(CHANNEL, 101L, "B1", 2L, BASE.plusMinutes(1));
        shouts.softDelete(deletedFromUser2, MOD);

        List<ShoutStats.ShouterCount> top = stats.topShouters(CHANNEL, 5);
        assertEquals(1, top.size());
        assertEquals(1L, top.get(0).userId());
    }

    @Test
    void oldestAndNewestPickByAuthoredAt() {
        shout(CHANNEL, 100L, "MIDDLE", 1L, BASE.plusMinutes(5));
        shout(CHANNEL, 101L, "FIRST",  2L, BASE);
        shout(CHANNEL, 102L, "LAST",   3L, BASE.plusMinutes(10));

        assertEquals("FIRST", stats.oldest(CHANNEL).orElseThrow().content());
        assertEquals("LAST",  stats.newest(CHANNEL).orElseThrow().content());
    }

    @Test
    void longestPicksByCharCount() {
        shout(CHANNEL, 100L, "SHORT",        1L, BASE);
        shout(CHANNEL, 101L, "MUCH LONGER",  1L, BASE.plusMinutes(1));
        shout(CHANNEL, 102L, "MEDIUM ONE",   1L, BASE.plusMinutes(2));
        assertEquals("MUCH LONGER", stats.longest(CHANNEL).orElseThrow().content());
    }

    @Test
    void mostReplayedJoinsHistoryAndCounts() {
        long s1 = shout(CHANNEL, 100L, "ONCE",     1L, BASE);
        long s2 = shout(CHANNEL, 101L, "TWICE",    1L, BASE.plusMinutes(1));
        long s3 = shout(CHANNEL, 102L, "THRICE",   1L, BASE.plusMinutes(2));

        history.record(CHANNEL, s1);
        history.record(CHANNEL, s2); history.record(CHANNEL, s2);
        history.record(CHANNEL, s3); history.record(CHANNEL, s3); history.record(CHANNEL, s3);

        ShoutStats.ReplayedShout top = stats.mostReplayed(CHANNEL).orElseThrow();
        assertEquals("THRICE", top.shout().content());
        assertEquals(3, top.replayCount());
    }

    @Test
    void mostReplayedSkipsDeletedShouts() {
        long popular = shout(CHANNEL, 100L, "WAS POPULAR", 1L, BASE);
        long alive   = shout(CHANNEL, 101L, "STILL HERE",   1L, BASE.plusMinutes(1));

        history.record(CHANNEL, popular); history.record(CHANNEL, popular); history.record(CHANNEL, popular);
        history.record(CHANNEL, alive);

        shouts.softDelete(popular, MOD);

        ShoutStats.ReplayedShout top = stats.mostReplayed(CHANNEL).orElseThrow();
        assertEquals("STILL HERE", top.shout().content());
        assertEquals(1, top.replayCount());
    }

    @Test
    void loadAllAssemblesEverything() {
        shout(CHANNEL, 100L, "OLD ONE",  1L, BASE);
        shout(CHANNEL, 101L, "NEW ONE",  2L, BASE.plusMinutes(5));
        long replayed = shout(CHANNEL, 102L, "FAMOUS LONGEST", 1L, BASE.plusMinutes(2));
        history.record(CHANNEL, replayed);
        history.record(CHANNEL, replayed);

        ShoutStats snapshot = stats.loadAll(CHANNEL, BASE.plusMinutes(10), 3);
        assertEquals(3, snapshot.totalShouts());
        assertEquals(2, snapshot.distinctShouters());
        assertEquals(3, snapshot.shoutsLast7Days());
        assertEquals("OLD ONE", snapshot.oldest().orElseThrow().content());
        assertEquals("NEW ONE", snapshot.newest().orElseThrow().content());
        assertEquals("FAMOUS LONGEST", snapshot.longest().orElseThrow().content());
        assertEquals("FAMOUS LONGEST", snapshot.mostReplayed().orElseThrow().shout().content());
        assertEquals(2, snapshot.mostReplayed().orElseThrow().replayCount());
    }

    private long shout(long channelId, long messageId, String content, long authorId, OffsetDateTime authoredAt) {
        shouts.tryInsert(channelId, messageId, content, authorId, authoredAt);
        return dsl.select(SHOUTS.ID).from(SHOUTS).where(SHOUTS.MESSAGE_ID.eq(messageId)).fetchOne(SHOUTS.ID);
    }
}
