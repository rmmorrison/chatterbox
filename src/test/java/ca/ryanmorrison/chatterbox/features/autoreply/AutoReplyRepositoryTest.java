package ca.ryanmorrison.chatterbox.features.autoreply;

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

import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.AUTO_REPLIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoReplyRepositoryTest {

    private static final long CHANNEL = 1L;
    private static final long AUTHOR  = 7777L;
    private static final long EDITOR  = 9999L;

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;
    private static DSLContext dsl;
    private AutoReplyRepository repo;

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
                .locations("classpath:db/migration/autoreply/postgresql")
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
        dsl.truncate(AUTO_REPLIES).restartIdentity().execute();
        repo = new AutoReplyRepository(dsl);
    }

    @Test
    void insertReturnsGeneratedId() {
        Optional<Long> id = repo.insert(CHANNEL, "(?i)hello", "Hi!", "Greets hello", AUTHOR);
        assertTrue(id.isPresent());

        AutoReplyRule rule = repo.findById(id.get()).orElseThrow();
        assertEquals("(?i)hello", rule.pattern());
        assertEquals("Hi!", rule.response());
        assertEquals("Greets hello", rule.description());
        assertEquals(AUTHOR, rule.createdBy());
        assertTrue(rule.edit().isEmpty());
    }

    @Test
    void duplicatePatternInSameChannelReturnsEmpty() {
        Optional<Long> first = repo.insert(CHANNEL, "(?i)hi", "Hi!", "first", AUTHOR);
        Optional<Long> second = repo.insert(CHANNEL, "(?i)hi", "Yo!", "second", AUTHOR);
        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
        assertEquals(1, dsl.fetchCount(AUTO_REPLIES));
    }

    @Test
    void duplicatePatternAcrossChannelsAllowed() {
        repo.insert(CHANNEL, "(?i)hi", "channel one", "first", AUTHOR);
        repo.insert(2L,      "(?i)hi", "channel two", "second", AUTHOR);
        assertEquals(2, dsl.fetchCount(AUTO_REPLIES));
    }

    @Test
    void updateStampsEditedFields() {
        long id = repo.insert(CHANNEL, "(?i)hi", "Hi!", "first", AUTHOR).orElseThrow();
        int rows = repo.update(id, "(?i)hello", "Hello there!", "Updated description", EDITOR);
        assertEquals(1, rows);

        AutoReplyRule rule = repo.findById(id).orElseThrow();
        assertEquals("(?i)hello", rule.pattern());
        assertEquals("Hello there!", rule.response());
        assertEquals("Updated description", rule.description());
        assertTrue(rule.edit().isPresent());
        assertEquals(EDITOR, rule.edit().orElseThrow().editedBy());
        // edited_at should differ from created_at
        assertNotEquals(rule.createdAt(), rule.edit().orElseThrow().editedAt());
    }

    @Test
    void updateOnMissingRowReturnsZero() {
        assertEquals(0, repo.update(9999L, "x", "y", "z", EDITOR));
    }

    @Test
    void deleteByIdRemovesRow() {
        long id = repo.insert(CHANNEL, "(?i)hi", "Hi!", "first", AUTHOR).orElseThrow();
        assertEquals(1, repo.deleteById(id));
        assertFalse(repo.findById(id).isPresent());
    }

    @Test
    void findByChannelAndPattern() {
        repo.insert(CHANNEL, "(?i)hi", "Hi!", "first", AUTHOR);
        Optional<AutoReplyRule> found = repo.findByChannelAndPattern(CHANNEL, "(?i)hi");
        assertTrue(found.isPresent());
        assertEquals("first", found.get().description());
        assertTrue(repo.findByChannelAndPattern(CHANNEL, "(?i)bye").isEmpty());
        assertTrue(repo.findByChannelAndPattern(2L, "(?i)hi").isEmpty());
    }

    @Test
    void listByChannelOrdersByIdAscending() {
        long a = repo.insert(CHANNEL, "(?i)a", "A", "first",  AUTHOR).orElseThrow();
        long b = repo.insert(CHANNEL, "(?i)b", "B", "second", AUTHOR).orElseThrow();
        long c = repo.insert(CHANNEL, "(?i)c", "C", "third",  AUTHOR).orElseThrow();
        var rules = repo.listByChannel(CHANNEL);
        assertEquals(3, rules.size());
        assertEquals(a, rules.get(0).id());
        assertEquals(b, rules.get(1).id());
        assertEquals(c, rules.get(2).id());
    }

    @Test
    void listRecentByChannelPutsRecentlyEditedFirst() {
        long a = repo.insert(CHANNEL, "(?i)a", "A", "first",  AUTHOR).orElseThrow();
        long b = repo.insert(CHANNEL, "(?i)b", "B", "second", AUTHOR).orElseThrow();
        long c = repo.insert(CHANNEL, "(?i)c", "C", "third",  AUTHOR).orElseThrow();
        // touch 'a' last by editing — its updated edited_at should bring it to the top.
        repo.update(a, "(?i)a-touched", "A!", "touched description", EDITOR);
        var rules = repo.listRecentByChannel(CHANNEL, 25);
        assertEquals(3, rules.size());
        assertEquals(a, rules.get(0).id(), "edited rule should be first");
        assertEquals(c, rules.get(1).id());
        assertEquals(b, rules.get(2).id());
    }

    @Test
    void countByChannel() {
        repo.insert(CHANNEL, "(?i)a", "A", "first",  AUTHOR);
        repo.insert(CHANNEL, "(?i)b", "B", "second", AUTHOR);
        repo.insert(2L,      "(?i)c", "C", "third",  AUTHOR);
        assertEquals(2, repo.countByChannel(CHANNEL));
        assertEquals(1, repo.countByChannel(2L));
    }
}
