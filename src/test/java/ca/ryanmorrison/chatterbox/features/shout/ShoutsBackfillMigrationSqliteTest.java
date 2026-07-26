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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Covers V20260726000000__shouts_backfill_authored_at.sql.
 *
 * <p>V20260430130000 added {@code authored_at} as {@code NOT NULL DEFAULT ''}
 * on SQLite, so every row that predated it was backfilled with the empty
 * string — which jOOQ then tries to read as an {@code OffsetDateTime}. The
 * original migration is already applied in production and must not be edited
 * (that would change its checksum), so the repair is a forward migration.
 *
 * <p>This reconstructs that history: migrate to the broken state, insert a row
 * the way the old schema would have left it, then migrate the rest of the way
 * and assert the repair landed.
 */
class ShoutsBackfillMigrationSqliteTest {

    private static final String LOCATION = "classpath:db/migration/shout/sqlite";
    private static final String BROKEN_VERSION = "20260430150000";

    private Path dbFile;
    private HikariDataSource dataSource;
    private DSLContext dsl;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("chatterbox-backfill-test", ".db");
        Files.delete(dbFile);

        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(hc);
        dsl = DSL.using(dataSource, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    private void migrateTo(String target) {
        Flyway.configure().dataSource(dataSource).locations(LOCATION).target(target)
                .load().migrate();
    }

    @Test
    void backfillRepairsEmptyStringAuthoredAt() {
        // Stop just before the repair, so authored_at still carries the '' default.
        migrateTo(BROKEN_VERSION);
        dsl.execute("INSERT INTO shouts (channel_id, message_id, content, created_at, "
                + "author_id, authored_at) VALUES (1, 100, 'HELLO WORLD', "
                + "'2026-04-30T12:00:00.000Z', 0, '')");
        assertEquals("", authoredAt(100L), "precondition: the broken state must reproduce");

        migrateTo("latest");

        assertEquals("2026-04-30T12:00:00.000Z", authoredAt(100L),
                "authored_at should fall back to created_at");
    }

    @Test
    void backfillLeavesGoodRowsAlone() {
        migrateTo(BROKEN_VERSION);
        dsl.execute("INSERT INTO shouts (channel_id, message_id, content, created_at, "
                + "author_id, authored_at) VALUES (1, 200, 'REAL SHOUT', "
                + "'2026-04-30T12:00:00.000Z', 7777, '2026-05-01T09:30:00.000Z')");

        migrateTo("latest");

        assertEquals("2026-05-01T09:30:00.000Z", authoredAt(200L));
        assertNotEquals("2026-04-30T12:00:00.000Z", authoredAt(200L));
    }

    @Test
    void backfillIsANoOpOnAFreshDatabase() {
        // A database created after the fix has no bad rows; the migration must
        // still apply cleanly and change nothing.
        migrateTo("latest");
        dsl.execute("INSERT INTO shouts (channel_id, message_id, content, created_at, "
                + "author_id, authored_at) VALUES (1, 300, 'FRESH', "
                + "'2026-07-01T00:00:00.000Z', 42, '2026-07-01T00:00:00.000Z')");

        assertEquals("2026-07-01T00:00:00.000Z", authoredAt(300L));
    }

    private String authoredAt(long messageId) {
        return dsl.fetchOne("SELECT authored_at FROM shouts WHERE message_id = ?", messageId)
                .get(0, String.class);
    }
}
