package ca.ryanmorrison.chatterbox.config.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigRegistryTest {

    @Test
    void emptyRegistryHasNoKeys() {
        ConfigRegistry r = new ConfigRegistry(List.of());
        assertTrue(r.all().isEmpty());
        assertTrue(r.find("nope").isEmpty());
    }

    @Test
    void allKeysAreSortedAlphabetically() {
        ConfigKey<Boolean> z = ConfigKey.bool("zzz", "ENV_Z", "true", "z");
        ConfigKey<Integer> a = ConfigKey.positiveInt("aaa", "ENV_A", "1", "a");
        ConfigKey<Boolean> m = ConfigKey.bool("mmm", "ENV_M", "false", "m");

        ConfigRegistry r = new ConfigRegistry(List.of(z, a, m));
        assertEquals(List.of("aaa", "mmm", "zzz"), r.all().stream().map(ConfigKey::key).toList());
    }

    @Test
    void findReturnsKeyByName() {
        ConfigKey<Boolean> k = ConfigKey.bool("foo.bar", "ENV", "true", "d");
        ConfigRegistry r = new ConfigRegistry(List.of(k));
        assertEquals(k, r.find("foo.bar").orElseThrow());
        assertTrue(r.find("foo.baz").isEmpty());
    }

    @Test
    void duplicateKeysRejected() {
        ConfigKey<Boolean> a = ConfigKey.bool("dup", "ENV_A", "true", "a");
        ConfigKey<Boolean> b = ConfigKey.bool("dup", "ENV_B", "false", "b");
        var ex = assertThrows(IllegalStateException.class, () -> new ConfigRegistry(List.of(a, b)));
        assertTrue(ex.getMessage().contains("dup"));
    }
}
