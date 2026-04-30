package ca.ryanmorrison.chatterbox.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers {@link Module} implementations on the classpath via the JDK
 * {@link ServiceLoader} mechanism.
 */
public final class ModuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModuleRegistry.class);

    private ModuleRegistry() {}

    public static List<Module> discover() {
        var modules = new ArrayList<Module>();
        for (Module m : ServiceLoader.load(Module.class)) {
            modules.add(m);
        }
        modules.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        log.info("Discovered {} module(s): {}", modules.size(),
                modules.stream().map(Module::name).toList());
        return List.copyOf(modules);
    }
}
