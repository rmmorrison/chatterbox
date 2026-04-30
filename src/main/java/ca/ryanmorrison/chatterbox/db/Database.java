package ca.ryanmorrison.chatterbox.db;

import ca.ryanmorrison.chatterbox.config.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * Lazy holder for the bot's shared connection pool and {@link DSLContext}.
 *
 * <p>The pool is built on first call to {@link #dsl()}, so a deployment that
 * never sets {@code CHATTERBOX_DB_URL} (or modules that never touch the DB)
 * pays no cost.
 */
public final class Database implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private final Optional<Config.DatabaseConfig> config;
    private volatile HikariDataSource dataSource;
    private volatile DSLContext dsl;

    public Database(Optional<Config.DatabaseConfig> config) {
        this.config = config;
    }

    public boolean isConfigured() {
        return config.isPresent();
    }

    /** Synchronous lazy init; cheap to call repeatedly once initialized. */
    public synchronized DSLContext dsl() {
        if (dsl != null) return dsl;
        var cfg = config.orElseThrow(() -> new IllegalStateException(
                "Database access requested but CHATTERBOX_DB_URL is not set."));
        this.dataSource = buildPool(cfg);
        this.dsl = DSL.using(dataSource, dialectFor(cfg));
        log.info("Database initialised: {}", redact(cfg.url()));
        return dsl;
    }

    /** Returns the underlying {@link DataSource}, initialising if needed. */
    public synchronized DataSource dataSource() {
        if (dataSource == null) {
            dsl(); // triggers init
        }
        return dataSource;
    }

    private static HikariDataSource buildPool(Config.DatabaseConfig cfg) {
        var hc = new HikariConfig();
        hc.setJdbcUrl(cfg.url());
        if (!cfg.user().isEmpty())     hc.setUsername(cfg.user());
        if (!cfg.password().isEmpty()) hc.setPassword(cfg.password());
        hc.setPoolName("chatterbox-pool");
        if (cfg.isSqlite()) {
            // SQLite serialises writes; one connection is the safe default.
            hc.setMaximumPoolSize(1);
        }
        return new HikariDataSource(hc);
    }

    private static SQLDialect dialectFor(Config.DatabaseConfig cfg) {
        if (cfg.isPostgres()) return SQLDialect.POSTGRES;
        if (cfg.isSqlite())   return SQLDialect.SQLITE;
        throw new IllegalArgumentException("Unsupported JDBC URL: " + cfg.url());
    }

    private static String redact(String url) {
        return url.replaceAll("(?i)(password=)[^&;]*", "$1***");
    }

    @Override
    public synchronized void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
            dsl = null;
        }
    }
}
