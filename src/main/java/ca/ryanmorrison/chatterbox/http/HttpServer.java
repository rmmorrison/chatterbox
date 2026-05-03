package ca.ryanmorrison.chatterbox.http;

import io.javalin.Javalin;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bot-wide HTTP server. Wraps a Javalin instance and exposes an {@link HttpRouter}
 * for modules to register routes against during bootstrap.
 *
 * <p>Lifecycle: build instance → modules register routes via {@link #router()} →
 * call {@link #start()} only if at least one route was registered. {@link #stop()}
 * is invoked from the shutdown hook regardless.
 *
 * <p>Routes are queued during the registration phase and applied when the
 * underlying Javalin instance is built in {@link #start()}, since Javalin 7
 * configures its router via the {@code Javalin.create(cfg -> ...)} callback.
 *
 * <p>The "start only if needed" check keeps the bot from binding port 8080 in
 * deployments where no module exposes HTTP endpoints.
 */
public final class HttpServer {

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

    private final int port;
    private final List<Consumer<RoutesConfig>> pendingRoutes = new ArrayList<>();
    private final HttpRouter router = new RoutingProxy();
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

    public void start() {
        if (started) return;
        app = Javalin.create(cfg -> {
            for (Consumer<RoutesConfig> r : pendingRoutes) {
                r.accept(cfg.routes);
            }
        });
        app.start(port);
        started = true;
        log.info("HTTP server listening on port {} ({} route(s) registered).", port, pendingRoutes.size());
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
