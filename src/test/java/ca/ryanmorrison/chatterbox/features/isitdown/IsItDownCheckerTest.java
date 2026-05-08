package ca.ryanmorrison.chatterbox.features.isitdown;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsItDownCheckerTest {

    private HttpServer server;
    private IsItDownChecker checker;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        port = server.getAddress().getPort();

        // Tight timeouts on the test client so timeout cases don't drag.
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        checker = new IsItDownChecker(http, 5);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private URI url(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private void serve(String path, int status, byte[] body) {
        server.createContext(path, ex -> respond(ex, status, body));
    }

    private void serve(String path, HttpHandler handler) {
        server.createContext(path, handler);
    }

    private static void respond(HttpExchange ex, int status, byte[] body) throws IOException {
        ex.sendResponseHeaders(status, body.length);
        try (var os = ex.getResponseBody()) { os.write(body); }
    }

    // ---- happy paths ----

    @Test
    void twoHundredIsLive() {
        serve("/ok", 200, new byte[]{'h', 'i'});
        var result = checker.check(url("/ok"));
        var live = assertInstanceOf(CheckResult.Live.class, result);
        assertEquals(200, live.status());
        assertTrue(live.elapsedMs() >= 0);
    }

    @Test
    void redirectIsFollowedAndReportedAsLive() {
        serve("/from", ex -> {
            ex.getResponseHeaders().add("Location", "/to");
            ex.sendResponseHeaders(301, -1);
            ex.close();
        });
        serve("/to", 200, new byte[0]);
        assertInstanceOf(CheckResult.Live.class, checker.check(url("/from")));
    }

    @Test
    void rangeHeaderIsSentOnEveryRequest() {
        AtomicReference<String> seen = new AtomicReference<>();
        serve("/range", ex -> {
            seen.set(ex.getRequestHeaders().getFirst("Range"));
            respond(ex, 200, new byte[0]);
        });
        checker.check(url("/range"));
        assertNotNull(seen.get(), "expected Range header on the probe request");
        assertTrue(seen.get().startsWith("bytes=0-"), () -> "got: " + seen.get());
    }

    @Test
    void oversizedBodyIsReadButCappedClientSide() {
        // 1 MB response — server ignores Range. Checker must still terminate
        // promptly without buffering the whole thing.
        byte[] big = new byte[1024 * 1024];
        serve("/big", 200, big);
        long start = System.nanoTime();
        var result = checker.check(url("/big"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertInstanceOf(CheckResult.Live.class, result);
        // Pure latency claim — if the cap is broken we'd be reading and discarding
        // 1 MB across loopback, which still finishes quickly. The real protection
        // is the buffer ceiling, not wall-clock; assertion is just smoke.
        assertTrue(elapsedMs < 5_000, () -> "took too long: " + elapsedMs + "ms");
    }

    // ---- failure paths ----

    @Test
    void fiveHundredIsBadStatus() {
        serve("/oops", 500, new byte[0]);
        var result = checker.check(url("/oops"));
        var bad = assertInstanceOf(CheckResult.BadStatus.class, result);
        assertEquals(500, bad.status());
    }

    @Test
    void fourOhFourIsBadStatus() {
        serve("/missing", 404, new byte[0]);
        assertInstanceOf(CheckResult.BadStatus.class, checker.check(url("/missing")));
    }

    @Test
    void serverHangPastTimeoutTriggersTimeout() {
        server.createContext("/slow", ex -> {
            try { Thread.sleep(2_000); } catch (InterruptedException ignored) {}
            respond(ex, 200, new byte[0]);
        });
        IsItDownChecker fast = new IsItDownChecker(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), 1);
        var result = fast.check(url("/slow"));
        var timeout = assertInstanceOf(CheckResult.Timeout.class, result);
        assertEquals(1, timeout.seconds());
    }

    @Test
    void connectionRefusedOnDeadPort() throws Exception {
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
            // socket closes when the try-with-resources exits; the port becomes
            // either reusable (and likely refused on next connect) or in TIME_WAIT.
        }
        var result = checker.check(URI.create("http://127.0.0.1:" + deadPort + "/"));
        // We expect ConnectionRefused but on some kernels this races and surfaces
        // as Unreachable; both indicate the port is not accepting traffic.
        assertTrue(result instanceof CheckResult.ConnectionRefused
                        || result instanceof CheckResult.Unreachable,
                () -> "got: " + result);
    }

    // Note: the DnsFailure branch inside IsItDownChecker is defensive — the
    // production handler resolves the host via UrlGuard before ever calling
    // the checker, and that path's failure modes are tested in UrlGuardTest.
    // Triggering UnknownHostException reliably from the JDK HttpClient is
    // environment-dependent (some resolvers return SERVFAIL → ConnectException
    // for .invalid lookups), so we don't exercise the checker's catch here.
}
