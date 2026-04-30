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
        this.dataSource = buildPool(config);
        this.dialect = dialectFor(config);
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
        throw new IllegalArgumentException("Unsupported JDBC URL: " + cfg.url());
    }

    private static String redact(String url) {
        return url.replaceAll("(?i)(password=)[^&;]*", "$1***");
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
