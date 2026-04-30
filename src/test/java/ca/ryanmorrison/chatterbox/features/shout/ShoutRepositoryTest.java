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

import java.util.List;
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoutRepositoryTest {

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
        dsl.truncate(SHOUTS).restartIdentity().execute();
        repo = new ShoutRepository(dsl);
    }

    @Test
    void insertThenFetchByMessageId() {
        repo.tryInsert(1L, 100L, "HELLO WORLD");
        assertEquals(Optional.of("HELLO WORLD"), repo.findContentByMessageId(100L));
    }

    @Test
    void duplicateContentInSameChannelIsIgnored() {
        repo.tryInsert(1L, 100L, "HELLO WORLD");
        repo.tryInsert(1L, 101L, "HELLO WORLD");
        assertEquals(1, dsl.fetchCount(SHOUTS));
        assertTrue(repo.findContentByMessageId(101L).isEmpty());
    }

    @Test
    void duplicateContentAcrossChannelsIsAllowed() {
        repo.tryInsert(1L, 100L, "HELLO WORLD");
        repo.tryInsert(2L, 200L, "HELLO WORLD");
        assertEquals(2, dsl.fetchCount(SHOUTS));
    }

    @Test
    void deleteByMessageId() {
        repo.tryInsert(1L, 100L, "HELLO WORLD");
        repo.deleteByMessageId(100L);
        assertEquals(0, dsl.fetchCount(SHOUTS));
    }

    @Test
    void deleteByMessageIdsHandlesEmptyList() {
        repo.tryInsert(1L, 100L, "HELLO WORLD");
        repo.deleteByMessageIds(List.of());
        assertEquals(1, dsl.fetchCount(SHOUTS));
    }

    @Test
    void deleteByMessageIdsRemovesAllListed() {
        repo.tryInsert(1L, 100L, "ONE TWO THREE FOUR FIVE");
        repo.tryInsert(1L, 101L, "SIX SEVEN EIGHT NINE TEN");
        repo.tryInsert(1L, 102L, "ELEVEN TWELVE THIRTEEN");
        repo.deleteByMessageIds(List.of(100L, 102L));
        assertEquals(1, dsl.fetchCount(SHOUTS));
        assertTrue(repo.findContentByMessageId(101L).isPresent());
    }

    @Test
    void updateRewritesContentWhenNoCollision() {
        repo.tryInsert(1L, 100L, "HELLO WORLD");
        repo.updateOrDeleteOnCollision(1L, 100L, "GOODBYE WORLD");
        assertEquals(Optional.of("GOODBYE WORLD"), repo.findContentByMessageId(100L));
    }

    @Test
    void updateOnCollisionDeletesOriginal() {
        repo.tryInsert(1L, 100L, "ORIGINAL CONTENT");
        repo.tryInsert(1L, 101L, "EXISTING TWIN HERE");
        repo.updateOrDeleteOnCollision(1L, 100L, "EXISTING TWIN HERE");
        assertTrue(repo.findContentByMessageId(100L).isEmpty(), "edited row should be deleted");
        assertEquals(Optional.of("EXISTING TWIN HERE"), repo.findContentByMessageId(101L),
                "twin should remain untouched");
    }

    @Test
    void randomPeerExcludesGivenMessage() {
        repo.tryInsert(1L, 100L, "FIRST SHOUT HERE");
        repo.tryInsert(1L, 101L, "SECOND SHOUT HERE");
        Optional<String> peer = repo.randomPeer(1L, 100L);
        assertEquals(Optional.of("SECOND SHOUT HERE"), peer);
    }

    @Test
    void randomPeerIsEmptyWhenChannelHasOnlyTheGivenMessage() {
        repo.tryInsert(1L, 100L, "ONLY ONE SHOUT");
        assertTrue(repo.randomPeer(1L, 100L).isEmpty());
    }

    @Test
    void randomPeerIsScopedToChannel() {
        repo.tryInsert(1L, 100L, "CHANNEL ONE SHOUT");
        repo.tryInsert(2L, 200L, "CHANNEL TWO SHOUT");
        assertEquals(Optional.of("CHANNEL ONE SHOUT"), repo.randomPeer(1L, 999L));
        assertEquals(Optional.of("CHANNEL TWO SHOUT"), repo.randomPeer(2L, 999L));
    }

    @Test
    void randomPeerIsEmptyWhenNothingStored() {
        assertFalse(repo.randomPeer(1L, 100L).isPresent());
    }
}
