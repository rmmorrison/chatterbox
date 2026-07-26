package ca.ryanmorrison.chatterbox.features.shout;

import ca.ryanmorrison.chatterbox.db.PostgresRepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dialect-parity coverage for {@link ShoutStatsRepository}, which was verified
 * against SQLite only.
 *
 * <p>It is the highest-risk repository for that gap: the generated jOOQ classes
 * are produced against PostgreSQL but executed against SQLite, and this class
 * leans on {@code charLength}, {@code countDistinct}, and a multi-column
 * {@code GROUP BY} — exactly the constructs whose rendered SQL differs between
 * the two.
 */
class ShoutStatsRepositoryPostgresTest extends PostgresRepositoryTestBase {

    private static final long CHANNEL = 1L;
    private static final long OTHER_CHANNEL = 2L;
    private static final OffsetDateTime T0 =
            OffsetDateTime.of(2026, 5, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    private ShoutRepository shouts;
    private ShoutHistoryRepository history;
    private ShoutStatsRepository stats;

    @Override
    protected String migrationLocation() {
        return "classpath:db/migration/shout/postgresql";
    }

    @BeforeEach
    void createRepositories() {
        shouts = new ShoutRepository(dsl);
        history = new ShoutHistoryRepository(dsl);
        stats = new ShoutStatsRepository(dsl);
    }

    private void shout(long messageId, String content, long author, OffsetDateTime at) {
        shouts.tryInsert(CHANNEL, messageId, content, author, at);
    }

    @Test
    void countsLiveShouts() {
        shout(100L, "HELLO", 10L, T0);
        shout(101L, "GOODBYE", 11L, T0);
        shouts.tryInsert(OTHER_CHANNEL, 102L, "ELSEWHERE", 12L, T0);

        assertEquals(2, stats.countLive(CHANNEL));
    }

    /** countDistinct renders differently per dialect. */
    @Test
    void countsDistinctShouters() {
        shout(100L, "ONE", 10L, T0);
        shout(101L, "TWO", 10L, T0);
        shout(102L, "THREE", 11L, T0);

        assertEquals(2, stats.countDistinctShouters(CHANNEL));
    }

    @Test
    void countsShoutsSinceATimestamp() {
        shout(100L, "OLD", 10L, T0.minusDays(30));
        shout(101L, "RECENT", 10L, T0.minusHours(1));

        assertEquals(1, stats.countLiveSince(CHANNEL, T0.minusDays(1)));
    }

    /** GROUP BY plus ordering on an aggregate. */
    @Test
    void ranksTopShouters() {
        shout(100L, "ONE", 10L, T0);
        shout(101L, "TWO", 10L, T0);
        shout(102L, "THREE", 11L, T0);

        var top = stats.topShouters(CHANNEL, 5);

        assertEquals(2, top.size());
        assertEquals(10L, top.get(0).userId());
        assertEquals(2, top.get(0).count());
    }

    @Test
    void findsOldestAndNewestByAuthoredAt() {
        shout(100L, "FIRST", 10L, T0.minusDays(2));
        shout(101L, "LAST", 11L, T0);

        assertEquals("FIRST", stats.oldest(CHANNEL).orElseThrow().content());
        assertEquals("LAST", stats.newest(CHANNEL).orElseThrow().content());
    }

    /** charLength is the construct most likely to differ across dialects. */
    @Test
    void findsTheLongestShoutByCharacterLength() {
        shout(100L, "SHORT", 10L, T0);
        shout(101L, "A MUCH LONGER SHOUT THAN THE OTHER ONE", 11L, T0);

        assertEquals("A MUCH LONGER SHOUT THAN THE OTHER ONE",
                stats.longest(CHANNEL).orElseThrow().content());
    }

    @Test
    void findsTheMostReplayedShout() {
        shout(100L, "POPULAR", 10L, T0);
        shout(101L, "IGNORED", 11L, T0);
        long popularId = shouts.randomPeer(CHANNEL, 101L).orElseThrow().shoutId();

        history.record(CHANNEL, popularId);
        history.record(CHANNEL, popularId);

        var most = stats.mostReplayed(CHANNEL).orElseThrow();
        assertEquals(2, most.replayCount());
    }

    /** The 8-query aggregate that backs the /shout stats embed. */
    @Test
    void loadAllReturnsACoherentSnapshot() {
        shout(100L, "HELLO THERE", 10L, T0.minusDays(3));
        shout(101L, "GOODBYE NOW", 11L, T0.minusHours(2));

        var all = stats.loadAll(CHANNEL, T0, 5);

        assertEquals(2, all.totalShouts());
        assertEquals(2, all.distinctShouters());
        assertTrue(all.oldest().isPresent());
        assertTrue(all.newest().isPresent());
        assertTrue(all.longest().isPresent());
    }

    @Test
    void softDeletedShoutsAreExcluded() {
        shout(100L, "VISIBLE", 10L, T0);
        shout(101L, "DELETED", 11L, T0);
        long id = shouts.randomPeer(CHANNEL, 100L).orElseThrow().shoutId();
        shouts.softDelete(id, 99L);

        assertEquals(1, stats.countLive(CHANNEL));
    }
}
