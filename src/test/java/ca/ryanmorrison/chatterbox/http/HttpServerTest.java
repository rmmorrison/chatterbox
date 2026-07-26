package ca.ryanmorrison.chatterbox.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpServerTest {

    private HttpServer server;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        URI uri = URI.create("http://localhost:" + port() + path);
        return http.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Javalin picks the port when configured with 0. */
    private int port() {
        return server.port();
    }

    @Test
    void healthReportsNotReadyBeforeTheProbeIsWired() throws Exception {
        // The window between binding the port and JDA connecting. Answering
        // 200 here is exactly what makes a container healthcheck worthless.
        server = new HttpServer(0);
        server.start();

        HttpResponse<String> res = get(HttpServer.HEALTH_PATH);

        assertEquals(503, res.statusCode());
        assertEquals("starting", res.body());
    }

    @Test
    void healthReportsReadyOnceTheProbePasses() throws Exception {
        var ready = new AtomicBoolean(false);
        server = new HttpServer(0);
        server.setReadiness(ready::get);
        server.start();

        assertEquals(503, get(HttpServer.HEALTH_PATH).statusCode());

        ready.set(true);
        HttpResponse<String> res = get(HttpServer.HEALTH_PATH);
        assertEquals(200, res.statusCode());
        assertEquals("ok", res.body());
    }

    @Test
    void aThrowingProbeReportsUnhealthyRatherThanErroring() throws Exception {
        server = new HttpServer(0);
        server.setReadiness(() -> { throw new IllegalStateException("jda exploded"); });
        server.start();

        assertEquals(503, get(HttpServer.HEALTH_PATH).statusCode());
    }

    @Test
    void healthWinsOverAModuleRouteThatCouldMatchIt() throws Exception {
        // The shortener registers GET /{token} at the root, and "health" is a
        // valid 6-character base36 token, so this is a genuine collision.
        server = new HttpServer(0);
        server.router().get("/{token}", ctx -> ctx.status(200).result("shortener"));
        server.setReadiness(() -> true);
        server.start();

        assertEquals("ok", get(HttpServer.HEALTH_PATH).body());
        assertEquals("shortener", get("/abc123").body());
    }

    @Test
    void serverStartsEvenWithNoModuleRoutes() throws Exception {
        server = new HttpServer(0);
        assertFalse(server.hasRoutes());

        server.start();

        // Previously the bot skipped the bind entirely in this case, leaving
        // nothing to probe.
        assertTrue(get(HttpServer.HEALTH_PATH).statusCode() > 0);
    }

    @Test
    void stopIsSafeToCallWhenNeverStarted() {
        server = new HttpServer(0);
        server.stop();
    }
}
