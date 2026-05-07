package ca.ryanmorrison.chatterbox.config.runtime;

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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end behaviour of {@link RuntimeConfig} against a real SQLite-backed
 * {@link RuntimeConfigRepository}. Covers the per-guild override / env var /
 * default precedence and cache invalidation on writes.
 */
class RuntimeConfigTest {

    private static final long GUILD = 12345L;
    private static final long ADMIN = 9999L;

    private static final ConfigKey<Boolean> ENABLED = ConfigKey.bool(
            "autoshorten.enabled", "CHATTERBOX_AUTOSHORTEN_ENABLED", "true", "Toggle auto-shortening.");
    private static final ConfigKey<Integer> THRESHOLD = ConfigKey.positiveInt(
            "autoshorten.threshold", "CHATTERBOX_AUTOSHORTEN_THRESHOLD", "160", "Min URL length.");

    private Path dbFile;
    private HikariDataSource dataSource;
    private RuntimeConfigRepository repo;
    private Map<String, String> env;
    private RuntimeConfig runtime;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("chatterbox-runtime-config-rt", ".db");
        Files.delete(dbFile);

        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(hc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/runtime-config/sqlite")
                .load()
                .migrate();

        DSLContext dsl = DSL.using(dataSource, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
        repo = new RuntimeConfigRepository(dsl);

        env = new HashMap<>();
        ConfigRegistry registry = new ConfigRegistry(List.of(ENABLED, THRESHOLD));
        runtime = new RuntimeConfig(registry, repo, env::get,
                Clock.fixed(Instant.parse("2026-05-07T12:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    @Test
    void defaultsApplyWhenNothingSet() {
        var resolved = runtime.resolve(GUILD, ENABLED);
        assertEquals(true, resolved.value());
        assertEquals(ConfigSource.DEFAULT, resolved.source());
        assertEquals("true", resolved.rawValue());

        assertEquals(160, runtime.integer(GUILD, THRESHOLD));
    }

    @Test
    void envVarBeatsDefault() {
        env.put("CHATTERBOX_AUTOSHORTEN_THRESHOLD", "200");
        var resolved = runtime.resolve(GUILD, THRESHOLD);
        assertEquals(200, resolved.value());
        assertEquals(ConfigSource.ENV_VAR, resolved.source());
    }

    @Test
    void perGuildOverrideBeatsEnvAndDefault() {
        env.put("CHATTERBOX_AUTOSHORTEN_THRESHOLD", "200");
        runtime.set(GUILD, THRESHOLD, "300", ADMIN);
        var resolved = runtime.resolve(GUILD, THRESHOLD);
        assertEquals(300, resolved.value());
        assertEquals(ConfigSource.GUILD_OVERRIDE, resolved.source());
        assertTrue(resolved.overrideRaw().isPresent());
    }

    @Test
    void perGuildOverrideAppliesOnlyToThatGuild() {
        runtime.set(GUILD, ENABLED, "false", ADMIN);
        assertFalse(runtime.bool(GUILD, ENABLED));
        // Different guild — falls back to default.
        assertTrue(runtime.bool(99L, ENABLED));
    }

    @Test
    void unsetRevertsToFallback() {
        env.put("CHATTERBOX_AUTOSHORTEN_ENABLED", "false");
        runtime.set(GUILD, ENABLED, "true", ADMIN);
        assertTrue(runtime.bool(GUILD, ENABLED));

        boolean removed = runtime.unset(GUILD, ENABLED);
        assertTrue(removed);
        // Falls through to env var.
        assertFalse(runtime.bool(GUILD, ENABLED));
        assertEquals(ConfigSource.ENV_VAR, runtime.resolve(GUILD, ENABLED).source());
    }

    @Test
    void unsetReturnsFalseWhenNoOverride() {
        assertFalse(runtime.unset(GUILD, ENABLED));
    }

    @Test
    void setRejectsInvalidValue() {
        assertThrows(ConfigType.InvalidValueException.class,
                () -> runtime.set(GUILD, THRESHOLD, "not-a-number", ADMIN));
        // Nothing persisted on rejection.
        assertEquals(160, runtime.integer(GUILD, THRESHOLD));
    }

    @Test
    void invalidStoredValueFallsThroughToEnv() {
        // Simulate a corrupted DB row by writing directly via the repo.
        repo.put(GUILD, "autoshorten.threshold", "garbage", ADMIN, java.time.OffsetDateTime.now());
        env.put("CHATTERBOX_AUTOSHORTEN_THRESHOLD", "250");
        // RuntimeConfig should log+skip the bogus override and fall through.
        var resolved = runtime.resolve(GUILD, THRESHOLD);
        assertEquals(250, resolved.value());
        assertEquals(ConfigSource.ENV_VAR, resolved.source());
    }

    @Test
    void cacheInvalidatesOnSet() {
        // Prime the cache with the default lookup.
        assertTrue(runtime.bool(GUILD, ENABLED));
        // Write directly via the repo — RuntimeConfig.set would invalidate, so
        // bypass it to prove the cache is otherwise being used.
        repo.put(GUILD, "autoshorten.enabled", "false", ADMIN, java.time.OffsetDateTime.now());
        // Cached: still sees true.
        assertTrue(runtime.bool(GUILD, ENABLED));
        // Now write through RuntimeConfig — that path invalidates the cache.
        runtime.set(GUILD, ENABLED, "false", ADMIN);
        assertFalse(runtime.bool(GUILD, ENABLED));
    }
}
