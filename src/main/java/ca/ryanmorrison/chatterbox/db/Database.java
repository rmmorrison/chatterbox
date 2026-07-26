package ca.ryanmorrison.chatterbox.db;

import ca.ryanmorrison.chatterbox.config.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Holder for the bot's shared connection pool and {@link DSLContext}. The pool
 * is created eagerly in the constructor — the database is a mandatory part of
 * the bot's runtime, so failures surface at startup rather than mid-request.
 */
public final class Database implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private final HikariDataSource dataSource;
    private final DSLContext dsl;
    private final SQLDialect dialect;

    public Database(Config.DatabaseConfig config) {
        // Dialect first: an unsupported URL should fail with our redacted
        // message, not with Hikari's, which echoes the raw jdbcUrl.
        this.dialect = dialectFor(config);
        this.dataSource = buildPool(config);
        this.dsl = DSL.using(dataSource, dialect, settingsFor(dialect));
        log.info("Database initialised: {}", redact(config.url()));
    }

    public DSLContext dsl() {
        return dsl;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public SQLDialect dialect() {
        return dialect;
    }

    /** Hikari's default is 30s, which is far too long to block a JDA listener thread. */
    private static final long CONNECTION_TIMEOUT_MS = 5_000L;
    /**
     * Warn about a connection held this long. Without it, a handler that leaks
     * a connection deadlocks the bot permanently and silently — on SQLite the
     * pool is a single connection, so one leak is total.
     */
    private static final long LEAK_DETECTION_MS = 20_000L;

    private static HikariDataSource buildPool(Config.DatabaseConfig cfg) {
        var hc = new HikariConfig();
        hc.setJdbcUrl(cfg.url());
        if (!cfg.user().isEmpty())     hc.setUsername(cfg.user());
        if (!cfg.password().isEmpty()) hc.setPassword(cfg.password());
        hc.setPoolName("chatterbox-pool");
        hc.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        hc.setLeakDetectionThreshold(LEAK_DETECTION_MS);
        if (cfg.isSqlite()) {
            // SQLite serialises writes; one connection is the safe default.
            hc.setMaximumPoolSize(1);
            // Everything funnels through that one connection: Jetty request
            // threads, every JDA listener thread, and the RSS refresh pool.
            //   - foreign_keys: off per-connection by default in SQLite, so
            //     ON DELETE CASCADE needs it re-enabled on every connection.
            //   - journal_mode=WAL: lets reads proceed during a write.
            //   - busy_timeout: wait for a lock rather than failing instantly
            //     with SQLITE_BUSY.
            //
            // Passed as driver properties, not as connectionInitSql: sqlite-jdbc
            // executes only the first statement of a multi-statement string, so
            // an init of "PRAGMA a; PRAGMA b; PRAGMA c" silently applies just
            // the first and leaves the rest at their defaults.
            hc.addDataSourceProperty("foreign_keys", "true");
            hc.addDataSourceProperty("journal_mode", "WAL");
            hc.addDataSourceProperty("busy_timeout", "5000");
        }
        return new HikariDataSource(hc);
    }

    /**
     * SQLite has no schemas, but the generated jOOQ classes are produced
     * against Postgres and embed a {@code public} schema reference. Disabling
     * schema rendering lets the same generated classes work against both
     * dialects.
     */
    private static Settings settingsFor(SQLDialect dialect) {
        return new Settings().withRenderSchema(dialect == SQLDialect.POSTGRES);
    }

    private static SQLDialect dialectFor(Config.DatabaseConfig cfg) {
        if (cfg.isPostgres()) return SQLDialect.POSTGRES;
        if (cfg.isSqlite())   return SQLDialect.SQLITE;
        // Redacted: this message reaches the log via Main's fatal-error handler,
        // and the raw URL may carry credentials.
        throw new IllegalArgumentException("Unsupported JDBC URL: " + redact(cfg.url()));
    }

    /**
     * Masks credentials in a JDBC URL. Covers both spellings that carry them:
     * the {@code password=} query parameter and the {@code //user:pass@host}
     * userinfo form, which the original pattern missed entirely.
     *
     * <p>Visible for testing.
     */
    static String redact(String url) {
        if (url == null) return "";
        String out = url.replaceAll("(?i)(password=)[^&;]*", "$1***");
        // scheme://user:secret@host -> scheme://user:***@host
        out = out.replaceAll("(?i)(//[^/@:]+):[^/@]*@", "$1:***@");
        return out;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
