package ca.ryanmorrison.chatterbox.config.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigKeyTest {

    @Test
    void boolFactoryProducesUsableKey() {
        ConfigKey<Boolean> k = ConfigKey.bool(
                "autoshorten.enabled", "CHATTERBOX_AUTOSHORTEN_ENABLED", "true",
                "Enable auto-shortening of long URLs.");
        assertEquals("autoshorten.enabled", k.key());
        assertEquals("CHATTERBOX_AUTOSHORTEN_ENABLED", k.envVar());
        assertEquals(Boolean.TRUE, k.defaultValue());
    }

    @Test
    void positiveIntFactoryProducesUsableKey() {
        ConfigKey<Integer> k = ConfigKey.positiveInt(
                "autoshorten.threshold", "CHATTERBOX_AUTOSHORTEN_THRESHOLD", "160", "min length");
        assertEquals(160, k.defaultValue());
    }

    @Test
    void invalidDefaultFailsAtConstruction() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> ConfigKey.positiveInt("k", "ENV", "not-a-number", "desc"));
        assertTrue(ex.getMessage().contains("Invalid default"));
    }

    @Test
    void zeroDefaultRejectedForPositiveInt() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigKey.positiveInt("k", "ENV", "0", "desc"));
    }

    @Test
    void nullsRejected() {
        assertThrows(NullPointerException.class,
                () -> new ConfigKey<>(null, "ENV", "1", ConfigType.POSITIVE_INTEGER, "d"));
        assertThrows(NullPointerException.class,
                () -> new ConfigKey<>("k", null, "1", ConfigType.POSITIVE_INTEGER, "d"));
    }
}
