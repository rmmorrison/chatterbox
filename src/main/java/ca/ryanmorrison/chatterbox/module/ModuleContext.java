package ca.ryanmorrison.chatterbox.module;

import ca.ryanmorrison.chatterbox.config.Config;
import net.dv8tion.jda.api.JDA;
import org.jooq.DSLContext;

/**
 * Per-module access to bot-wide services. Passed to {@link Module#onStart}.
 *
 * <p>The database is always available — modules choose whether to use it.
 */
public interface ModuleContext {

    JDA jda();

    Config config();

    /** The bot's shared {@link DSLContext}, backed by the connection pool. */
    DSLContext database();
}
