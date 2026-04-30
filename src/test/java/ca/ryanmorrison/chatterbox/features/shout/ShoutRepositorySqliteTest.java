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
import java.util.Optional;

import static ca.ryanmorrison.chatterbox.db.generated.Tables.SHOUTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity check that the SQLite migration matches the Postgres one closely
 * enough that {@link ShoutRepository} works against either dialect using the
 * Postgres-generated jOOQ classes. Generated columns the repo never selects
 * (e.g. {@code created_at}) may differ in SQL type without causing issues.
 */
class ShoutRepositorySqliteTest {

    private Path dbFile;
    private HikariDataSource dataSource;
    private DSLContext dsl;
    private ShoutRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("chatterbox-test", ".db");
        Files.delete(dbFile); // Flyway/SQLite create on first connection

        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
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
        repo.tryInsert(1L, 100L, "HELLO WORLD");
        assertEquals(Optional.of("HELLO WORLD"), repo.findContentByMessageId(100L));
    }

    @Test
    void duplicateInsertIsIgnoredAgainstSqlite() {
        repo.tryInsert(1L, 100L, "HELLO WORLD");
        repo.tryInsert(1L, 101L, "HELLO WORLD");
        assertEquals(1, dsl.fetchCount(SHOUTS));
    }

    @Test
    void updateCollisionDeletesOriginalAgainstSqlite() {
        repo.tryInsert(1L, 100L, "ORIGINAL CONTENT HERE");
        repo.tryInsert(1L, 101L, "EXISTING TWIN HERE");
        repo.updateOrDeleteOnCollision(1L, 100L, "EXISTING TWIN HERE");
        assertTrue(repo.findContentByMessageId(100L).isEmpty());
        assertEquals(Optional.of("EXISTING TWIN HERE"), repo.findContentByMessageId(101L));
    }
}
