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
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity check that the SQLite migration matches the Postgres one closely
 * enough that {@link ShoutRepository} works against either dialect using the
 * Postgres-generated jOOQ classes.
 */
class ShoutRepositorySqliteTest {

    private static final OffsetDateTime AUTHORED_AT =
            OffsetDateTime.of(2026, 4, 30, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final long AUTHOR = 7777L;

    private Path dbFile;
    private HikariDataSource dataSource;
    private DSLContext dsl;
    private ShoutRepository repo;

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
        repo = new ShoutRepository(dsl);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    @Test
    void insertAndFetchAgainstSqlite() {
        repo.tryInsert(1L, 100L, "HELLO WORLD", AUTHOR, AUTHORED_AT);
        assertEquals(Optional.of("HELLO WORLD"), repo.findContentByMessageId(100L));
    }

    @Test
    void duplicateInsertIsIgnoredAgainstSqlite() {
        repo.tryInsert(1L, 100L, "HELLO WORLD", AUTHOR, AUTHORED_AT);
        repo.tryInsert(1L, 101L, "HELLO WORLD", AUTHOR, AUTHORED_AT);
        assertEquals(1, dsl.fetchCount(SHOUTS));
    }

    @Test
    void updateCollisionDeletesOriginalAgainstSqlite() {
        repo.tryInsert(1L, 100L, "ORIGINAL CONTENT HERE", AUTHOR, AUTHORED_AT);
        repo.tryInsert(1L, 101L, "EXISTING TWIN HERE", AUTHOR, AUTHORED_AT);
        repo.updateOrDeleteOnCollision(1L, 100L, "EXISTING TWIN HERE");
        assertTrue(repo.findContentByMessageId(100L).isEmpty());
        assertEquals(Optional.of("EXISTING TWIN HERE"), repo.findContentByMessageId(101L));
    }
}
