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
 * just {@link #slashCommands(InitContext)}; one that only listens to events
 * overrides just {@link #listeners(InitContext)}; one that needs persistence
 * declares its {@link #migrationLocations()} and pulls a {@code DSLContext}
 * from {@link InitContext#database()}.
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
     * globally or per-guild depending on {@code CHATTERBOX_DEV_MODE}. The
     * {@link InitContext} provides the database and config so command builders
     * can use bot-wide resources directly.
     */
    default List<SlashCommandData> slashCommands(InitContext ctx) { return List.of(); }

    /**
     * Event listeners to attach to JDA. Called before JDA is built, so the
     * returned listeners are visible to startup events too. The
     * {@link InitContext} gives modules access to the database and config so
     * listeners can be constructed fully-wired.
     */
    default List<EventListener> listeners(InitContext ctx) { return List.of(); }

    /**
     * Base classpath locations (relative to {@code src/main/resources}) holding
     * Flyway migrations. Each base is resolved to a dialect-specific subfolder
     * ({@code postgresql/} or {@code sqlite/}) at runtime. Skip if the module
     * does not use the database.
     *
     * <p>Layout, for a base of {@code db/migration/<module>}:
     * <pre>
     *   db/migration/&lt;module&gt;/postgresql/V20260430120000__init.sql
     *   db/migration/&lt;module&gt;/sqlite/V20260430120000__init.sql
     * </pre>
     *
     * <p>Use timestamp-based version numbers to avoid collisions across modules.
     */
    default List<String> migrationLocations() { return List.of(); }

    /**
     * Invoked after JDA is fully ready ({@link ReadyEvent} fired) and after
     * slash commands have been synced. Use this for post-ready actions that
     * need a live {@link net.dv8tion.jda.api.JDA} reference (e.g. inspecting
     * the bot's guild membership). Database wiring belongs in
     * {@link #listeners(InitContext)} / {@link #slashCommands(InitContext)}.
     */
    default void onStart(ModuleContext ctx) {}

    /**
     * Invoked during graceful shutdown, before JDA is closed. Release any
     * module-owned resources here.
     */
    default void onStop() {}
}
