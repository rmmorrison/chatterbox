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

/** Verifies the SQLite migration is wire-compatible with the generated jOOQ classes. */
class AutoReplyRepositorySqliteTest {

    private static final long CHANNEL = 1L;
    private static final long AUTHOR  = 7777L;
    private static final long EDITOR  = 9999L;

    private Path dbFile;
    private HikariDataSource dataSource;
    private DSLContext dsl;
    private AutoReplyRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("chatterbox-test", ".db");
        Files.delete(dbFile);

        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
        hc.setConnectionInitSql("PRAGMA foreign_keys = ON");
        dataSource = new HikariDataSource(hc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/autoreply/sqlite")
                .load()
                .migrate();

        dsl = DSL.using(dataSource, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
        repo = new AutoReplyRepository(dsl);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    @Test
    void insertAndFetchAgainstSqlite() {
        Optional<Long> id = repo.insert(CHANNEL, "(?i)hello", "Hi!", "Greets hello", AUTHOR);
        assertTrue(id.isPresent());
        assertEquals("Hi!", repo.findById(id.get()).orElseThrow().response());
    }

    @Test
    void duplicatePatternIgnoredAgainstSqlite() {
        repo.insert(CHANNEL, "(?i)hi", "Hi!", "first",  AUTHOR);
        Optional<Long> dup = repo.insert(CHANNEL, "(?i)hi", "Yo!", "second", AUTHOR);
        assertTrue(dup.isEmpty());
        assertEquals(1, dsl.fetchCount(AUTO_REPLIES));
    }

    @Test
    void updateStampsEditedFieldsAgainstSqlite() {
        long id = repo.insert(CHANNEL, "(?i)hi", "Hi!", "first", AUTHOR).orElseThrow();
        repo.update(id, "(?i)hello", "Hello!", "updated", EDITOR);
        AutoReplyRule rule = repo.findById(id).orElseThrow();
        assertTrue(rule.edit().isPresent());
        assertEquals(EDITOR, rule.edit().orElseThrow().editedBy());
    }
}
