package ca.ryanmorrison.chatterbox;

import ca.ryanmorrison.chatterbox.commands.CommandSync;
import ca.ryanmorrison.chatterbox.config.Config;
import ca.ryanmorrison.chatterbox.config.runtime.ConfigKey;
import ca.ryanmorrison.chatterbox.config.runtime.ConfigRegistry;
import ca.ryanmorrison.chatterbox.config.runtime.RuntimeConfig;
import ca.ryanmorrison.chatterbox.config.runtime.RuntimeConfigRepository;
import ca.ryanmorrison.chatterbox.db.Database;
import ca.ryanmorrison.chatterbox.db.Migrations;
import ca.ryanmorrison.chatterbox.http.HttpServer;
import ca.ryanmorrison.chatterbox.module.InitContext;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Bootstrap {

    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    /** How long to let JDA drain in-flight events before closing the DB pool. */
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    /** Follow-up grace period after shutdownNow(). */
    private static final Duration FORCED_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    public static void run() throws InterruptedException {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        Config config = Config.fromEnvironment();
        log.info("Starting Chatterbox (devMode={})", config.devMode());

        List<Module> modules = ModuleRegistry.discover();

        Database database = new Database(config.database());
        HttpServer httpServer = new HttpServer(config.http().port());
        try {
            start(config, modules, database, httpServer);
        } catch (InterruptedException | RuntimeException | Error e) {
            // The shutdown hook isn't registered until the very end of start(),
            // so anything thrown before then -- a Flyway failure, a duplicate
            // config key, a module blowing up, an interrupt during
            // awaitReady() -- would otherwise leave the Hikari pool and the
            // bound HTTP port behind. The process is exiting either way, but
            // this also keeps run() usable from a test.
            closeQuietly(httpServer::stop, "HTTP server");
            closeQuietly(database::close, "database");
            throw e;
        }
    }

    private static void start(Config config,
                              List<Module> modules,
                              Database database,
                              HttpServer httpServer) throws InterruptedException {
        var migrationLocations = new ArrayList<String>();
        modules.forEach(m -> migrationLocations.addAll(m.migrationLocations()));
        // The /config slash command lives in a feature module too, but its
        // table needs to exist regardless of whether that module loads (other
        // modules read RuntimeConfig at message time). Bootstrap owns this
        // migration so the runtime-config feature can never be silently absent.
        migrationLocations.add("classpath:db/migration/runtime-config");
        Migrations.run(database.dataSource(), migrationLocations, database.dialect());

        var configKeys = new ArrayList<ConfigKey<?>>();
        modules.forEach(m -> configKeys.addAll(m.configKeys()));
        ConfigRegistry configRegistry = new ConfigRegistry(configKeys);
        RuntimeConfig runtimeConfig = new RuntimeConfig(
                configRegistry, new RuntimeConfigRepository(database.dsl()));

        InitContext initCtx = new InitContextImpl(config, database.dsl(), runtimeConfig);

        Set<GatewayIntent> intents = new HashSet<>();
        EnumSet<CacheFlag> cacheFlags = EnumSet.noneOf(CacheFlag.class);
        List<SlashCommandData> commands = new ArrayList<>();
        List<EventListener> listeners = new ArrayList<>();

        for (Module m : modules) {
            // Guarded the same way onStart is below. Modules arrive via a
            // ServiceLoader SPI, so a third-party one throwing here used to
            // take the whole bot down, while the identical failure in onStart
            // was logged and skipped.
            try {
                intents.addAll(m.intents());
                cacheFlags.addAll(m.cacheFlags());
                commands.addAll(m.slashCommands(initCtx));
                listeners.addAll(m.listeners(initCtx));
                m.registerHttpRoutes(httpServer.router(), initCtx);
            } catch (RuntimeException e) {
                log.error("Module {} failed to initialise; continuing without it.", m.name(), e);
            }
        }
        log.info("Aggregated {} intent(s), {} command(s), {} listener(s) across {} module(s).",
                intents.size(), commands.size(), listeners.size(), modules.size());

        if (httpServer.hasRoutes()) {
            httpServer.start();
        } else {
            log.info("No modules registered HTTP routes; skipping HTTP server bind.");
        }

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

        ModuleContext ctx = new ModuleContextImpl(jda, config, database.dsl(), runtimeConfig);
        for (Module m : modules) {
            try {
                m.onStart(ctx);
                log.info("Started module: {}", m.name());
            } catch (RuntimeException e) {
                log.error("Module {} failed to start; continuing.", m.name(), e);
            }
        }

        installShutdownHook(jda, modules, database, httpServer);
    }

    private static void installShutdownHook(JDA jda, List<Module> modules, Database database, HttpServer httpServer) {
        var shuttingDown = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!shuttingDown.compareAndSet(false, true)) return;
            log.info("Shutdown signal received.");
            for (Module m : modules) {
                // Throwable, not RuntimeException: an Error from any one module
                // used to skip the HTTP stop, the JDA shutdown and the pool
                // close for everything else.
                try {
                    m.onStop();
                } catch (Throwable t) {
                    log.warn("Module {} failed during shutdown.", m.name(), t);
                }
            }
            closeQuietly(httpServer::stop, "HTTP server");
            // JDA#shutdown is asynchronous, so closing the pool straight after
            // tore it down while listener threads were still draining in-flight
            // events -- an in-flight handler would fail mid-write with
            // "HikariDataSource has been closed". Wait for the drain first.
            try {
                jda.shutdown();
                if (!jda.awaitShutdown(SHUTDOWN_TIMEOUT)) {
                    log.warn("JDA didn't finish shutting down within {}s; forcing.",
                            SHUTDOWN_TIMEOUT.toSeconds());
                    jda.shutdownNow();
                    jda.awaitShutdown(FORCED_SHUTDOWN_TIMEOUT);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for JDA to shut down.");
            } catch (RuntimeException e) {
                log.warn("JDA shutdown failed.", e);
            }
            closeQuietly(database::close, "database");
            log.info("Shutdown complete.");
        }, "chatterbox-shutdown"));
    }

    /** Runs a cleanup step, logging rather than propagating so later steps still run. */
    private static void closeQuietly(Runnable step, String what) {
        try {
            step.run();
        } catch (Throwable t) {
            log.warn("Failed to shut down the {} cleanly.", what, t);
        }
    }

    private record InitContextImpl(Config config, DSLContext database, RuntimeConfig runtimeConfig)
            implements InitContext {}
    private record ModuleContextImpl(JDA jda, Config config, DSLContext database, RuntimeConfig runtimeConfig)
            implements ModuleContext {}

    private Bootstrap() {}
}
