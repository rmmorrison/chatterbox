package ca.ryanmorrison.chatterbox;

import ca.ryanmorrison.chatterbox.commands.CommandSync;
import ca.ryanmorrison.chatterbox.config.Config;
import ca.ryanmorrison.chatterbox.db.Database;
import ca.ryanmorrison.chatterbox.db.Migrations;
import ca.ryanmorrison.chatterbox.module.Module;
import ca.ryanmorrison.chatterbox.module.ModuleContext;
import ca.ryanmorrison.chatterbox.module.ModuleRegistry;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Bootstrap {

    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    public static void run() throws InterruptedException {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        Config config = Config.fromEnvironment();
        log.info("Starting Chatterbox (devMode={}, db={})",
                config.devMode(), config.database().isPresent() ? "configured" : "disabled");

        List<Module> modules = ModuleRegistry.discover();

        Database database = new Database(config.database());
        runMigrationsIfNeeded(modules, database, config);

        Set<GatewayIntent> intents = new HashSet<>();
        EnumSet<CacheFlag> cacheFlags = EnumSet.noneOf(CacheFlag.class);
        List<SlashCommandData> commands = new ArrayList<>();
        List<EventListener> listeners = new ArrayList<>();

        for (Module m : modules) {
            intents.addAll(m.intents());
            cacheFlags.addAll(m.cacheFlags());
            commands.addAll(m.slashCommands());
            listeners.addAll(m.listeners());
        }
        log.info("Aggregated {} intent(s), {} command(s), {} listener(s) across {} module(s).",
                intents.size(), commands.size(), listeners.size(), modules.size());

        CommandSync commandSync = new CommandSync(commands, config.devMode());

        JDABuilder builder = JDABuilder.createLight(config.discordToken(), intents)
                .enableCache(cacheFlags)
                .addEventListeners(commandSync);
        for (EventListener l : listeners) {
            builder.addEventListeners(l);
        }
        JDA jda = builder.build();
        jda.awaitReady();
        log.info("JDA ready. Connected as {} in {} guild(s).",
                jda.getSelfUser().getName(), jda.getGuilds().size());

        commandSync.syncAll(jda);

        ModuleContext ctx = new ContextImpl(jda, config, database);
        for (Module m : modules) {
            try {
                m.onStart(ctx);
                log.info("Started module: {}", m.name());
            } catch (RuntimeException e) {
                log.error("Module {} failed to start; continuing.", m.name(), e);
            }
        }

        installShutdownHook(jda, modules, database);
    }

    private static void runMigrationsIfNeeded(List<Module> modules, Database database, Config config) {
        var locations = modules.stream()
                .flatMap(m -> m.migrationLocations().stream())
                .toList();
        if (locations.isEmpty()) return;
        if (!database.isConfigured()) {
            throw new IllegalStateException(
                    "Modules declared migrations (" + locations + ") but CHATTERBOX_DB_URL is not set.");
        }
        Migrations.run(database.dataSource(), locations);
    }

    private static void installShutdownHook(JDA jda, List<Module> modules, Database database) {
        var shuttingDown = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!shuttingDown.compareAndSet(false, true)) return;
            log.info("Shutdown signal received.");
            for (Module m : modules) {
                try {
                    m.onStop();
                } catch (RuntimeException e) {
                    log.warn("Module {} failed during shutdown.", m.name(), e);
                }
            }
            jda.shutdown();
            database.close();
            log.info("Shutdown complete.");
        }, "chatterbox-shutdown"));
    }

    private record ContextImpl(JDA jda, Config config, Database db) implements ModuleContext {
        @Override public Optional<DSLContext> database() {
            return db.isConfigured() ? Optional.of(db.dsl()) : Optional.empty();
        }
    }

    private Bootstrap() {}
}
