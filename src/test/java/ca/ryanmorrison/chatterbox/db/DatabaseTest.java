package ca.ryanmorrison.chatterbox.db;

import ca.ryanmorrison.chatterbox.config.Config;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    private static Config.DatabaseConfig sqlite(Path file) {
        return new Config.DatabaseConfig("jdbc:sqlite:" + file, "", "");
    }

    /**
     * The SQLite init SQL sets three PRAGMAs in one statement string. Whether
     * the driver accepts a multi-statement init is not obvious from the Hikari
     * API, and getting it wrong fails at first connection — i.e. at startup, in
     * production. So actually open a connection and read the settings back.
     */
    @Test
    void sqlitePoolAppliesItsInitPragmas() throws Exception {
        Path file = Files.createTempFile("chatterbox-db-test", ".db");
        Files.delete(file);
        try (var db = new Database(sqlite(file));
             var conn = db.dataSource().getConnection();
             var st = conn.createStatement()) {

            try (var rs = st.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "ON DELETE CASCADE depends on this");
            }
            try (var rs = st.executeQuery("PRAGMA journal_mode")) {
                assertTrue(rs.next());
                assertEquals("wal", rs.getString(1).toLowerCase(java.util.Locale.ROOT));
            }
            try (var rs = st.executeQuery("PRAGMA busy_timeout")) {
                assertTrue(rs.next());
                assertEquals(5000, rs.getInt(1));
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(Path.of(file + "-wal"));
            Files.deleteIfExists(Path.of(file + "-shm"));
        }
    }

    @Test
    void detectsDialectFromTheUrl() throws Exception {
        Path file = Files.createTempFile("chatterbox-db-dialect", ".db");
        Files.delete(file);
        try (var db = new Database(sqlite(file))) {
            assertEquals(SQLDialect.SQLITE, db.dialect());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void rejectsAnUnsupportedUrlWithoutLeakingCredentials() {
        var cfg = new Config.DatabaseConfig(
                "jdbc:mysql://chatterbox:hunter2@db/chatterbox", "", "");

        var e = assertThrows(RuntimeException.class, () -> new Database(cfg));

        // Main logs this message on a fatal startup error, so the password
        // must not ride along into the container log.
        assertFalse(String.valueOf(e.getMessage()).contains("hunter2"),
                () -> "credential leaked into: " + e.getMessage());
    }

    // ---- redact ----

    @Test
    void redactMasksThePasswordQueryParameter() {
        assertEquals("jdbc:postgresql://db/chatterbox?user=bot&password=***",
                Database.redact("jdbc:postgresql://db/chatterbox?user=bot&password=hunter2"));
    }

    @Test
    void redactMasksUserInfoCredentials() {
        // The form the original pattern missed entirely.
        assertEquals("jdbc:postgresql://bot:***@db/chatterbox",
                Database.redact("jdbc:postgresql://bot:hunter2@db/chatterbox"));
    }

    @Test
    void redactLeavesCredentialFreeUrlsAlone() {
        assertEquals("jdbc:sqlite:/data/chatterbox.db",
                Database.redact("jdbc:sqlite:/data/chatterbox.db"));
        assertEquals("jdbc:postgresql://db:5432/chatterbox",
                Database.redact("jdbc:postgresql://db:5432/chatterbox"));
    }

    @Test
    void redactToleratesNull() {
        assertEquals("", Database.redact(null));
    }
}
