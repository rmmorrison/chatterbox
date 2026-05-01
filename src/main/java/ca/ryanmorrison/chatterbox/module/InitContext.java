package ca.ryanmorrison.chatterbox.module;

import ca.ryanmorrison.chatterbox.config.Config;
import org.jooq.DSLContext;

/**
 * Resources available to a module before JDA is built. Passed to
 * {@link Module#listeners(InitContext)} and {@link Module#slashCommands(InitContext)}
 * so modules can construct fully-wired event handlers and command builders
 * up front.
 */
public interface InitContext {
    Config config();
    DSLContext database();
}
