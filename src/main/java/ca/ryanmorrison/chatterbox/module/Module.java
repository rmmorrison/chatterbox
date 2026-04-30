package ca.ryanmorrison.chatterbox.module;

import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * SPI for self-registering bot features. Implementations live anywhere on the
 * classpath and are discovered via {@link java.util.ServiceLoader} — register
 * the FQCN in {@code META-INF/services/ca.ryanmorrison.chatterbox.module.Module}.
 *
 * <p>Every hook is optional. A module that only adds a slash command overrides
 * just {@link #slashCommands()}; one that only listens to events overrides just
 * {@link #listeners()}; one that needs persistence declares its
 * {@link #migrationLocations()} and pulls a {@code DSLContext} from
 * {@link ModuleContext#database()}.
 */
public interface Module {

    /** Stable, human-readable name used for logging. */
    String name();

    /**
     * Gateway intents this module needs. Bootstrap unions intents across all
     * modules before building JDA.
     */
    default Set<GatewayIntent> intents() { return Set.of(); }

    /**
     * Cache flags this module needs. Bootstrap unions across all modules.
     */
    default EnumSet<CacheFlag> cacheFlags() { return EnumSet.noneOf(CacheFlag.class); }

    /**
     * Slash commands the module contributes. Returned data is registered either
     * globally or per-guild depending on {@code CHATTERBOX_DEV_MODE}.
     */
    default List<SlashCommandData> slashCommands() { return List.of(); }

    /**
     * Event listeners to attach to JDA. Listeners are added before JDA's
     * {@code build()} so they observe startup events too.
     */
    default List<EventListener> listeners() { return List.of(); }

    /**
     * Classpath locations (relative to {@code src/main/resources}) holding
     * Flyway migrations. Bootstrap aggregates these across modules and runs
     * Flyway once over the union. Skip if the module does not use the database.
     *
     * <p>Use timestamp-based version numbers (e.g. {@code V20260430120000__init.sql})
     * to avoid collisions across modules.
     */
    default List<String> migrationLocations() { return List.of(); }

    /**
     * Invoked after JDA is fully ready ({@link ReadyEvent} fired) and after
     * slash commands have been synced. Modules that need a {@code DSLContext}
     * obtain it lazily via {@link ModuleContext#database()}.
     */
    default void onStart(ModuleContext ctx) {}

    /**
     * Invoked during graceful shutdown, before JDA is closed. Release any
     * module-owned resources here.
     */
    default void onStop() {}
}
