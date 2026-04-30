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
import java.util.List;
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoutRepositoryTest {

    private static final OffsetDateTime AUTHORED_AT =
            OffsetDateTime.of(2026, 4, 30, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final long AUTHOR = 7777L;

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;
    private static DSLContext dsl;
    private ShoutRepository repo;

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
        repo = new ShoutRepository(dsl);
    }

    private void insert(long channelId, long messageId, String content) {
        repo.tryInsert(channelId, messageId, content, AUTHOR, AUTHORED_AT);
    }

    @Test
    void insertThenFetchByMessageId() {
        insert(1L, 100L, "HELLO WORLD");
        assertEquals(Optional.of("HELLO WORLD"), repo.findContentByMessageId(100L));
    }

    @Test
    void insertPersistsAuthorAndAuthoredAt() {
        insert(1L, 100L, "HELLO WORLD");
        var row = dsl.selectFrom(SHOUTS).where(SHOUTS.MESSAGE_ID.eq(100L)).fetchOne();
        assertEquals(AUTHOR, row.getAuthorId());
        assertEquals(AUTHORED_AT.toInstant(), row.getAuthoredAt().toInstant());
    }

    @Test
    void duplicateContentInSameChannelIsIgnored() {
        insert(1L, 100L, "HELLO WORLD");
        insert(1L, 101L, "HELLO WORLD");
        assertEquals(1, dsl.fetchCount(SHOUTS));
        assertTrue(repo.findContentByMessageId(101L).isEmpty());
    }

    @Test
    void duplicateContentAcrossChannelsIsAllowed() {
        insert(1L, 100L, "HELLO WORLD");
        insert(2L, 200L, "HELLO WORLD");
        assertEquals(2, dsl.fetchCount(SHOUTS));
    }

    @Test
    void deleteByMessageId() {
        insert(1L, 100L, "HELLO WORLD");
        repo.deleteByMessageId(100L);
        assertEquals(0, dsl.fetchCount(SHOUTS));
    }

    @Test
    void deleteByMessageIdsHandlesEmptyList() {
        insert(1L, 100L, "HELLO WORLD");
        repo.deleteByMessageIds(List.of());
        assertEquals(1, dsl.fetchCount(SHOUTS));
    }

    @Test
    void deleteByMessageIdsRemovesAllListed() {
        insert(1L, 100L, "ONE TWO THREE FOUR FIVE");
        insert(1L, 101L, "SIX SEVEN EIGHT NINE TEN");
        insert(1L, 102L, "ELEVEN TWELVE THIRTEEN");
        repo.deleteByMessageIds(List.of(100L, 102L));
        assertEquals(1, dsl.fetchCount(SHOUTS));
        assertTrue(repo.findContentByMessageId(101L).isPresent());
    }

    @Test
    void updateRewritesContentWhenNoCollision() {
        insert(1L, 100L, "HELLO WORLD");
        repo.updateOrDeleteOnCollision(1L, 100L, "GOODBYE WORLD");
        assertEquals(Optional.of("GOODBYE WORLD"), repo.findContentByMessageId(100L));
    }

    @Test
    void updateOnCollisionDeletesOriginal() {
        insert(1L, 100L, "ORIGINAL CONTENT");
        insert(1L, 101L, "EXISTING TWIN HERE");
        repo.updateOrDeleteOnCollision(1L, 100L, "EXISTING TWIN HERE");
        assertTrue(repo.findContentByMessageId(100L).isEmpty(), "edited row should be deleted");
        assertEquals(Optional.of("EXISTING TWIN HERE"), repo.findContentByMessageId(101L),
                "twin should remain untouched");
    }

    @Test
    void randomPeerReturnsFullPayload() {
        insert(1L, 100L, "FIRST SHOUT HERE");
        insert(1L, 101L, "SECOND SHOUT HERE");
        Optional<Peer> peer = repo.randomPeer(1L, 100L);
        assertTrue(peer.isPresent());
        assertEquals("SECOND SHOUT HERE", peer.get().content());
        assertEquals(AUTHOR, peer.get().authorId());
        assertEquals(AUTHORED_AT.toInstant(), peer.get().authoredAt().toInstant());
    }

    @Test
    void randomPeerIsEmptyWhenChannelHasOnlyTheGivenMessage() {
        insert(1L, 100L, "ONLY ONE SHOUT");
        assertTrue(repo.randomPeer(1L, 100L).isEmpty());
    }

    @Test
    void randomPeerIsScopedToChannel() {
        insert(1L, 100L, "CHANNEL ONE SHOUT");
        insert(2L, 200L, "CHANNEL TWO SHOUT");
        assertEquals("CHANNEL ONE SHOUT", repo.randomPeer(1L, 999L).orElseThrow().content());
        assertEquals("CHANNEL TWO SHOUT", repo.randomPeer(2L, 999L).orElseThrow().content());
    }

    @Test
    void randomPeerIsEmptyWhenNothingStored() {
        assertFalse(repo.randomPeer(1L, 100L).isPresent());
    }
}
