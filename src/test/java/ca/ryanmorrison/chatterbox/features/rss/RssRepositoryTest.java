package ca.ryanmorrison.chatterbox.features.rss;

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

import static ca.ryanmorrison.chatterbox.db.generated.Tables.RSS_FEEDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RssRepositoryTest {

    private static final long GUILD   = 100L;
    private static final long CHANNEL = 1L;
    private static final long USER    = 7777L;

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;
    private static DSLContext dsl;
    private RssRepository repo;

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
                .locations("classpath:db/migration/rss/postgresql")
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
        dsl.truncate(RSS_FEEDS).restartIdentity().execute();
        repo = new RssRepository(dsl);
    }

    @Test
    void insertReturnsHydratedFeed() {
        Optional<Feed> created = repo.insert(GUILD, CHANNEL,
                "https://example.com/feed", "Example Feed", USER, 60);
        assertTrue(created.isPresent());
        Feed f = created.get();
        assertEquals(GUILD, f.guildId());
        assertEquals(CHANNEL, f.channelId());
        assertEquals("Example Feed", f.title());
        assertEquals(60, f.refreshMinutes());
        assertTrue(f.lastItemId().isEmpty());
        assertTrue(f.lastRefreshedAt().isEmpty());
    }

    @Test
    void duplicateUrlInSameChannelReturnsEmpty() {
        Optional<Feed> first  = repo.insert(GUILD, CHANNEL, "https://x/feed", "X", USER, 30);
        Optional<Feed> second = repo.insert(GUILD, CHANNEL, "https://x/feed", "X", USER, 60);
        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
        assertEquals(1, dsl.fetchCount(RSS_FEEDS));
    }

    @Test
    void duplicateUrlAcrossChannelsAllowed() {
        repo.insert(GUILD, CHANNEL, "https://x/feed", "X", USER, 30);
        repo.insert(GUILD, 2L,      "https://x/feed", "X", USER, 30);
        assertEquals(2, dsl.fetchCount(RSS_FEEDS));
    }

    @Test
    void updateMarkersWritesAllThreeFields() {
        long id = repo.insert(GUILD, CHANNEL, "https://x/feed", "X", USER, 30).orElseThrow().id();
        OffsetDateTime pub = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime ref = OffsetDateTime.now(ZoneOffset.UTC);
        repo.updateMarkers(id, "guid-1", pub, ref);
        Feed f = repo.findById(id).orElseThrow();
        assertEquals(Optional.of("guid-1"), f.lastItemId());
        assertTrue(f.lastItemPublishedAt().isPresent());
        assertTrue(f.lastRefreshedAt().isPresent());
    }

    @Test
    void touchRefreshedAtLeavesMarkersAlone() {
        long id = repo.insert(GUILD, CHANNEL, "https://x/feed", "X", USER, 30).orElseThrow().id();
        repo.updateMarkers(id, "guid-1", OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        repo.touchRefreshedAt(id, OffsetDateTime.now(ZoneOffset.UTC));
        Feed f = repo.findById(id).orElseThrow();
        assertEquals(Optional.of("guid-1"), f.lastItemId(), "marker preserved");
    }

    @Test
    void deleteByIdAndByChannel() {
        long a = repo.insert(GUILD, CHANNEL, "https://a", "A", USER, 30).orElseThrow().id();
        long b = repo.insert(GUILD, CHANNEL, "https://b", "B", USER, 30).orElseThrow().id();
        repo.insert(GUILD, 2L, "https://c", "C", USER, 30);

        assertEquals(1, repo.deleteById(a));
        assertFalse(repo.findById(a).isPresent());

        assertEquals(1, repo.deleteByChannel(CHANNEL));
        assertFalse(repo.findById(b).isPresent());
        assertEquals(1, dsl.fetchCount(RSS_FEEDS));
    }

    @Test
    void listByChannelAndCount() {
        repo.insert(GUILD, CHANNEL, "https://a", "A", USER, 30);
        repo.insert(GUILD, CHANNEL, "https://b", "B", USER, 30);
        repo.insert(GUILD, 2L,      "https://c", "C", USER, 30);
        assertEquals(2, repo.listByChannel(CHANNEL).size());
        assertEquals(2, repo.countByChannel(CHANNEL));
        assertEquals(1, repo.countByChannel(2L));
    }

    @Test
    void listAllReturnsEverything() {
        repo.insert(GUILD, CHANNEL, "https://a", "A", USER, 30);
        repo.insert(GUILD, 2L,      "https://b", "B", USER, 30);
        assertEquals(2, repo.listAll().size());
    }
}
