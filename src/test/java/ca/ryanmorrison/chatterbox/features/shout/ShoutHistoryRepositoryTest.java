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
        Optional<HistoryEntry> latest = history.findLatest(1L);
        assertTrue(latest.isPresent());
        assertEquals("FIRST SHOUT IN CHANNEL", latest.get().content());
        assertEquals(AUTHOR, latest.get().authorId());
    }

    @Test
    void findLatestIsEmptyWhenNoHistory() {
        assertFalse(history.findLatest(1L).isPresent());
    }

    @Test
    void findLatestIsScopedToChannel() {
        long c1 = shout(1L, 100L, "CHANNEL ONE SHOUT");
        long c2 = shout(2L, 200L, "CHANNEL TWO SHOUT");
        history.record(1L, c1);
        history.record(2L, c2);
        assertEquals("CHANNEL ONE SHOUT", history.findLatest(1L).orElseThrow().content());
        assertEquals("CHANNEL TWO SHOUT", history.findLatest(2L).orElseThrow().content());
    }

    @Test
    void paginateOlderAndNewer() {
        long s1 = shout(1L, 100L, "FIRST SHOUT IN HISTORY");
        long s2 = shout(1L, 101L, "SECOND SHOUT IN HISTORY");
        long s3 = shout(1L, 102L, "THIRD SHOUT IN HISTORY");
        history.record(1L, s1);
        history.record(1L, s2);
        history.record(1L, s3);

        HistoryEntry latest = history.findLatest(1L).orElseThrow();
        assertEquals("THIRD SHOUT IN HISTORY", latest.content());

        HistoryEntry older = history.findOlder(1L, latest.historyId()).orElseThrow();
        assertEquals("SECOND SHOUT IN HISTORY", older.content());

        HistoryEntry oldest = history.findOlder(1L, older.historyId()).orElseThrow();
        assertEquals("FIRST SHOUT IN HISTORY", oldest.content());

        assertFalse(history.findOlder(1L, oldest.historyId()).isPresent());

        HistoryEntry backNewer = history.findNewer(1L, oldest.historyId()).orElseThrow();
        assertEquals("SECOND SHOUT IN HISTORY", backNewer.content());

        assertFalse(history.findNewer(1L, latest.historyId()).isPresent());
    }

    @Test
    void positionAtNewestIsRankOne() {
        long s1 = shout(1L, 100L, "FIRST SHOUT IN HISTORY");
        long s2 = shout(1L, 101L, "SECOND SHOUT IN HISTORY");
        history.record(1L, s1);
        history.record(1L, s2);
        HistoryEntry latest = history.findLatest(1L).orElseThrow();
        var pos = history.position(1L, latest.historyId());
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

        HistoryEntry latest = history.findLatest(1L).orElseThrow();
        HistoryEntry middle = history.findOlder(1L, latest.historyId()).orElseThrow();
        HistoryEntry oldest = history.findOlder(1L, middle.historyId()).orElseThrow();

        assertEquals(2, history.position(1L, middle.historyId()).rank());
        assertEquals(3, history.position(1L, oldest.historyId()).rank());
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
        assertEquals("SURVIVING SHOUT FOREVER", history.findLatest(1L).orElseThrow().content());
    }

    @Test
    void editCollisionCascadesHistoryForOriginalRow() {
        long s1 = shout(1L, 100L, "ORIGINAL CONTENT HERE");
        long s2 = shout(1L, 101L, "EXISTING TWIN CONTENT");
        history.record(1L, s1); // emit original a couple of times before the edit
        history.record(1L, s1);
        history.record(1L, s2);
        assertEquals(3, dsl.fetchCount(SHOUT_HISTORY));

        shouts.updateOrDeleteOnCollision(1L, 100L, "EXISTING TWIN CONTENT");
        // s1 deleted (folded into s2), so its 2 history rows cascade-deleted.
        assertEquals(1, dsl.fetchCount(SHOUT_HISTORY));
        assertEquals("EXISTING TWIN CONTENT", history.findLatest(1L).orElseThrow().content());
    }
}
