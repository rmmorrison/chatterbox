package ca.ryanmorrison.chatterbox.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @Test
    void defaultsAreApplied() {
        Config cfg = Config.fromEnvironment(Map.of("CHATTERBOX_DISCORD_TOKEN", "abc")::get);
        assertEquals("abc", cfg.discordToken());
        assertFalse(cfg.devMode());
        assertEquals("INFO", cfg.logLevel());
        assertTrue(cfg.database().isEmpty());
    }

    @Test
    void devModeAndDbConfigAreParsed() {
        Config cfg = Config.fromEnvironment(Map.of(
                "CHATTERBOX_DISCORD_TOKEN", "tok",
                "CHATTERBOX_DEV_MODE", "true",
                "CHATTERBOX_DB_URL", "jdbc:postgresql://localhost/db",
                "CHATTERBOX_DB_USER", "u",
                "CHATTERBOX_DB_PASSWORD", "p"
        )::get);
        assertTrue(cfg.devMode());
        assertTrue(cfg.database().isPresent());
        assertEquals("u", cfg.database().get().user());
        assertTrue(cfg.database().get().isPostgres());
    }

    @Test
    void missingTokenThrows() {
        assertThrows(IllegalStateException.class,
                () -> Config.fromEnvironment(Map.<String, String>of()::get));
    }
}
