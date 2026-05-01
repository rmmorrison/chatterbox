package ca.ryanmorrison.chatterbox.features.autoreply;

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
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.AUTO_REPLIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backed by an in-memory SQLite (no Docker needed) to validate the
 * matcher's caching and ordering behaviour against a real repository.
 */
class AutoReplyMatcherTest {

    private static final long CHANNEL = 1L;
    private static final long AUTHOR  = 7777L;

    private Path dbFile;
    private HikariDataSource dataSource;
    private DSLContext dsl;
    private AutoReplyRepository repo;
    private AutoReplyMatcher matcher;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("chatterbox-test", ".db");
        Files.delete(dbFile);

        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(hc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/autoreply/sqlite")
                .load()
                .migrate();

        dsl = DSL.using(dataSource, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
        repo = new AutoReplyRepository(dsl);
        matcher = new AutoReplyMatcher(repo);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    @Test
    void firstMatchReturnsResponse() {
        repo.insert(CHANNEL, "(?i)hello", "Hi there!", "greet hello", AUTHOR);
        Optional<String> result = matcher.firstMatch(CHANNEL, "well, HELLO world");
        assertEquals(Optional.of("Hi there!"), result);
    }

    @Test
    void firstMatchWinsWhenMultipleRulesApply() {
        // Lower id (inserted first) wins the precedence battle.
        repo.insert(CHANNEL, "(?i)hello", "First wins!", "first", AUTHOR);
        repo.insert(CHANNEL, "(?i)hello world", "Second loses", "second", AUTHOR);
        Optional<String> result = matcher.firstMatch(CHANNEL, "hello world");
        assertEquals(Optional.of("First wins!"), result);
    }

    @Test
    void noMatchReturnsEmpty() {
        repo.insert(CHANNEL, "(?i)hello", "Hi there!", "greet", AUTHOR);
        assertTrue(matcher.firstMatch(CHANNEL, "good morning").isEmpty());
    }

    @Test
    void invalidateForcesReload() {
        repo.insert(CHANNEL, "(?i)hello", "Hi!", "greet", AUTHOR);
        // Warm cache.
        assertEquals(Optional.of("Hi!"), matcher.firstMatch(CHANNEL, "hello"));

        // Add a higher-priority rule (lower id wins, so our existing rule still wins
        // unless we INSTEAD edit the existing one).
        long id = dsl.select(AUTO_REPLIES.ID).from(AUTO_REPLIES).fetchOne(AUTO_REPLIES.ID);
        repo.update(id, "(?i)hello", "Updated reply", "still greets", AUTHOR);

        // Without invalidation the cache still serves the stale response.
        assertEquals(Optional.of("Hi!"), matcher.firstMatch(CHANNEL, "hello"));

        matcher.invalidate(CHANNEL);
        assertEquals(Optional.of("Updated reply"), matcher.firstMatch(CHANNEL, "hello"));
    }

    @Test
    void rulesAreScopedToChannel() {
        repo.insert(CHANNEL, "(?i)hello", "channel one reply", "first",  AUTHOR);
        repo.insert(2L,      "(?i)hello", "channel two reply", "second", AUTHOR);
        assertEquals(Optional.of("channel one reply"), matcher.firstMatch(CHANNEL, "hello"));
        assertEquals(Optional.of("channel two reply"), matcher.firstMatch(2L, "hello"));
    }

    @Test
    void timedOutRulesAreSkippedRatherThanCrashing() {
        // Timeout of 0ms reliably trips the watchdog on the first charAt of every rule —
        // the matcher should swallow each timeout and return empty rather than propagating.
        var instantTimeout = new AutoReplyMatcher(repo, 0L);
        repo.insert(CHANNEL, "(?i)hello", "first reply",  "first",  AUTHOR);
        repo.insert(CHANNEL, "(?i)world", "second reply", "second", AUTHOR);
        assertTrue(instantTimeout.firstMatch(CHANNEL, "hello world").isEmpty(),
                "all-rules-time-out should return empty, not throw");
    }
}
