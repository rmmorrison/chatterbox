package ca.ryanmorrison.chatterbox.module;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleRegistryTest {

    @Test
    void discoversPingModuleViaServiceLoader() {
        List<Module> modules = ModuleRegistry.discover();
        assertFalse(modules.isEmpty(), "expected at least one module to be discovered");
        assertTrue(modules.stream().anyMatch(m -> "ping".equals(m.name())),
                "expected the ping module to self-register");
    }
}
