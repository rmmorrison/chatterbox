package ca.ryanmorrison.chatterbox.module;

import ca.ryanmorrison.chatterbox.config.Config;
import net.dv8tion.jda.api.JDA;
import org.jooq.DSLContext;

import java.util.Optional;

/**
 * Per-module access to bot-wide services. Passed to {@link Module#onStart}.
 *
 * <p>The database accessor is {@link Optional} so modules that don't need
 * persistence pay nothing — and modules that do can fail loudly at startup
 * rather than silently mid-runtime.
 */
public interface ModuleContext {

    JDA jda();

    Config config();

    /**
     * @return a {@code DSLContext} backed by the bot-wide connection pool, or
     *         empty if no database is configured ({@code CHATTERBOX_DB_URL}
     *         unset).
     */
    Optional<DSLContext> database();
}
