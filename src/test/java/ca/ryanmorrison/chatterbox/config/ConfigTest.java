package ca.ryanmorrison.chatterbox.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @Test
    void minimalRequiredEnvIsAccepted() {
        Config cfg = Config.fromEnvironment(Map.of(
                "CHATTERBOX_DISCORD_TOKEN", "abc",
                "CHATTERBOX_DB_URL", "jdbc:sqlite:./chatterbox.db"
        )::get);
        assertEquals("abc", cfg.discordToken());
        assertFalse(cfg.devMode());
        assertEquals("INFO", cfg.logLevel());
        assertTrue(cfg.database().isSqlite());
        assertEquals("", cfg.database().user());
    }

    @Test
    void devModeAndPostgresConfigAreParsed() {
        Config cfg = Config.fromEnvironment(Map.of(
                "CHATTERBOX_DISCORD_TOKEN", "tok",
                "CHATTERBOX_DEV_MODE", "true",
                "CHATTERBOX_DB_URL", "jdbc:postgresql://localhost/db",
                "CHATTERBOX_DB_USER", "u",
                "CHATTERBOX_DB_PASSWORD", "p"
        )::get);
        assertTrue(cfg.devMode());
        assertTrue(cfg.database().isPostgres());
        assertEquals("u", cfg.database().user());
        assertEquals("p", cfg.database().password());
    }

    @Test
    void missingTokenThrows() {
        assertThrows(IllegalStateException.class,
                () -> Config.fromEnvironment(Map.of(
                        "CHATTERBOX_DB_URL", "jdbc:sqlite:./x.db"
                )::get));
    }

    @Test
    void missingDbUrlThrows() {
        assertThrows(IllegalStateException.class,
                () -> Config.fromEnvironment(Map.of(
                        "CHATTERBOX_DISCORD_TOKEN", "abc"
                )::get));
    }

    @Test
    void autoShortenDefaultsToEnabledAt160() {
        Config cfg = Config.fromEnvironment(Map.of(
                "CHATTERBOX_DISCORD_TOKEN", "abc",
                "CHATTERBOX_DB_URL", "jdbc:sqlite:./x.db"
        )::get);
        assertTrue(cfg.shortener().autoShortenEnabled());
        assertEquals(160, cfg.shortener().autoShortenThreshold());
    }

    @Test
    void autoShortenCanBeDisabled() {
        Config cfg = Config.fromEnvironment(Map.of(
                "CHATTERBOX_DISCORD_TOKEN", "abc",
                "CHATTERBOX_DB_URL", "jdbc:sqlite:./x.db",
                "CHATTERBOX_AUTOSHORTEN_ENABLED", "false"
        )::get);
        assertFalse(cfg.shortener().autoShortenEnabled());
    }

    @Test
    void autoShortenThresholdIsParsed() {
        Config cfg = Config.fromEnvironment(Map.of(
                "CHATTERBOX_DISCORD_TOKEN", "abc",
                "CHATTERBOX_DB_URL", "jdbc:sqlite:./x.db",
                "CHATTERBOX_AUTOSHORTEN_THRESHOLD", "200"
        )::get);
        assertEquals(200, cfg.shortener().autoShortenThreshold());
    }

    @Test
    void autoShortenThresholdMustBePositive() {
        assertThrows(IllegalStateException.class,
                () -> Config.fromEnvironment(Map.of(
                        "CHATTERBOX_DISCORD_TOKEN", "abc",
                        "CHATTERBOX_DB_URL", "jdbc:sqlite:./x.db",
                        "CHATTERBOX_AUTOSHORTEN_THRESHOLD", "0"
                )::get));
    }

    @Test
    void autoShortenThresholdMustBeAnInteger() {
        assertThrows(IllegalStateException.class,
                () -> Config.fromEnvironment(Map.of(
                        "CHATTERBOX_DISCORD_TOKEN", "abc",
                        "CHATTERBOX_DB_URL", "jdbc:sqlite:./x.db",
                        "CHATTERBOX_AUTOSHORTEN_THRESHOLD", "not-a-number"
                )::get));
    }
}
