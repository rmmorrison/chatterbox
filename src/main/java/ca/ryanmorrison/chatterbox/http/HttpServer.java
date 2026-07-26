package ca.ryanmorrison.chatterbox.http;

import io.javalin.Javalin;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Bot-wide HTTP server. Wraps a Javalin instance and exposes an {@link HttpRouter}
 * for modules to register routes against during bootstrap.
 *
 * <p>Lifecycle: build instance → modules register routes via {@link #router()} →
 * call {@link #start()}. {@link #stop()} is invoked from the shutdown hook.
 *
 * <p>Routes are queued during the registration phase and applied when the
 * underlying Javalin instance is built in {@link #start()}, since Javalin 7
 * configures its router via the {@code Javalin.create(cfg -> ...)} callback.
 *
 * <p>The server always binds, because it always serves {@link #HEALTH_PATH}.
 * That is a change from the previous "start only if a module registered a
 * route": with no always-present endpoint there was nothing for a container
 * healthcheck to probe, so a hung bot stayed "up" indefinitely and Traefik
 * routed to it the moment the process started.
 */
public final class HttpServer {

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

    /** Liveness/readiness endpoint. Owned here, not by a module, so it always exists. */
    public static final String HEALTH_PATH = "/health";

    private final int port;
    private final List<Consumer<RoutesConfig>> pendingRoutes = new ArrayList<>();
    private final HttpRouter router = new RoutingProxy();

    /**
     * Reports whether the bot is actually usable. Defaults to "no": the server
     * binds before JDA connects, and answering 200 during that window is what
     * makes a healthcheck useless.
     */
    private volatile BooleanSupplier readiness = () -> false;

    private Javalin app;
    private boolean started;

    public HttpServer(int port) {
        this.port = port;
    }

    /** Route registration surface for modules. */
    public HttpRouter router() {
        return router;
    }

    /** True once at least one module has registered a route. */
    public boolean hasRoutes() {
        return !pendingRoutes.isEmpty();
    }

    /**
     * Sets the predicate behind {@link #HEALTH_PATH}. Bootstrap wires this to
     * JDA's connection status once the gateway is up.
     */
    public void setReadiness(BooleanSupplier probe) {
        this.readiness = probe == null ? () -> false : probe;
    }

    public void start() {
        if (started) return;
        app = Javalin.create(cfg -> {
            // Registered before module routes so it wins the match. The URL
            // shortener claims GET /{token} at the root, and "health" is a
            // syntactically valid 6-character token, so order decides this.
            cfg.routes.get(HEALTH_PATH, ctx -> {
                boolean ok = isReady();
                ctx.status(ok ? 200 : 503)
                        .contentType("text/plain; charset=utf-8")
                        .result(ok ? "ok" : "starting");
            });
            for (Consumer<RoutesConfig> r : pendingRoutes) {
                r.accept(cfg.routes);
            }
        });
        app.start(port);
        started = true;
        log.info("HTTP server listening on port {} ({} module route(s) registered, plus {}).",
                port, pendingRoutes.size(), HEALTH_PATH);
    }

    /**
     * The bound port, or -1 if not started. Useful when the configured port is
     * 0 and the OS picked one.
     */
    public int port() {
        return started && app != null ? app.port() : -1;
    }

    private boolean isReady() {
        try {
            return readiness.getAsBoolean();
        } catch (RuntimeException e) {
            // A probe that throws is not a healthy bot.
            log.warn("Readiness probe failed: {}", e.toString());
            return false;
        }
    }

    public void stop() {
        if (!started || app == null) return;
        try {
            app.stop();
        } catch (RuntimeException e) {
            log.warn("HTTP server failed to stop cleanly.", e);
        }
        started = false;
    }

    private final class RoutingProxy implements HttpRouter {
        @Override public HttpRouter get(String path, Handler h)    { pendingRoutes.add(r -> r.get(path, h));    return this; }
        @Override public HttpRouter post(String path, Handler h)   { pendingRoutes.add(r -> r.post(path, h));   return this; }
        @Override public HttpRouter put(String path, Handler h)    { pendingRoutes.add(r -> r.put(path, h));    return this; }
        @Override public HttpRouter patch(String path, Handler h)  { pendingRoutes.add(r -> r.patch(path, h));  return this; }
        @Override public HttpRouter delete(String path, Handler h) { pendingRoutes.add(r -> r.delete(path, h)); return this; }
    }
}
