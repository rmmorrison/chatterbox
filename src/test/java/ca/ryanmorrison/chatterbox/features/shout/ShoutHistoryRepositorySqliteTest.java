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

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUTS;
import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUT_HISTORY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the SQLite migration includes the FK with cascade and that
 * Hikari's connection-init {@code PRAGMA foreign_keys = ON} actually applies
 * — without it, SQLite silently ignores the constraint.
 */
class ShoutHistoryRepositorySqliteTest {

    private static final OffsetDateTime AUTHORED_AT =
            OffsetDateTime.of(2026, 4, 30, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final long AUTHOR = 7777L;

    private Path dbFile;
    private HikariDataSource dataSource;
    private DSLContext dsl;
    private ShoutRepository shouts;
    private ShoutHistoryRepository history;

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
                .locations("classpath:db/migration/shout/sqlite")
                .load()
                .migrate();

        dsl = DSL.using(dataSource, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
        shouts = new ShoutRepository(dsl);
        history = new ShoutHistoryRepository(dsl);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    private long shout(long channelId, long messageId, String content) {
        shouts.tryInsert(channelId, messageId, content, AUTHOR, AUTHORED_AT);
        return dsl.select(SHOUTS.ID).from(SHOUTS).where(SHOUTS.MESSAGE_ID.eq(messageId)).fetchOne(SHOUTS.ID);
    }

    @Test
    void recordAndFindLatestAgainstSqlite() {
        long s1 = shout(1L, 100L, "HELLO WORLD AGAIN");
        history.record(1L, s1);
        assertTrue(history.findLatest(1L).isPresent());
        assertEquals("HELLO WORLD AGAIN", history.findLatest(1L).orElseThrow().content());
    }

    @Test
    void deleteCascadesAgainstSqlite() {
        long s1 = shout(1L, 100L, "DOOMED SHOUT FOREVER");
        history.record(1L, s1);
        assertEquals(1, dsl.fetchCount(SHOUT_HISTORY));
        shouts.deleteByMessageId(100L);
        assertEquals(0, dsl.fetchCount(SHOUT_HISTORY),
                "FK cascade should remove history rows when the shout is deleted");
    }
}
