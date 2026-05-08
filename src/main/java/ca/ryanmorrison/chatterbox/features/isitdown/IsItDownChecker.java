package ca.ryanmorrison.chatterbox.features.isitdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;

/**
 * Probes a URL and classifies the outcome into a {@link CheckResult}.
 *
 * <h2>Cost controls</h2>
 * <ul>
 *   <li>{@link #REQUEST_TIMEOUT}-second total deadline (connect + headers +
 *       body up to the cap), enforced via the request's per-call timeout.</li>
 *   <li>{@code Range: bytes=0-1023} on the GET so well-behaved servers send
 *       only 1 KB; for servers that ignore the header, the body stream is
 *       read up to {@link #MAX_RESPONSE_BYTES} client-side and then closed.</li>
 *   <li>Redirects followed by the underlying client (so a typical
 *       {@code http → https} 301 still reports as live).</li>
 * </ul>
 *
 * <h2>Failure mapping</h2>
 * Every recognised {@code IOException} subclass maps to a {@link CheckResult}
 * variant; the original exception is logged at WARN here but never returned to
 * the caller, so user-facing messages can be rendered safely.
 *
 * <h2>SSRF</h2>
 * The constructor expects a URI that has already passed {@link UrlGuard}'s
 * resolve check. The class itself does no extra deny-listing — keeping the
 * security check in one place avoids drift.
 */
final class IsItDownChecker {

    private static final Logger log = LoggerFactory.getLogger(IsItDownChecker.class);

    static final int REQUEST_TIMEOUT = 10;
    static final int CONNECT_TIMEOUT = 4;
    /** Hard cap on bytes read from the response body; sites that ignore Range are still bounded. */
    static final int MAX_RESPONSE_BYTES = 4 * 1024;
    static final String USER_AGENT = "Chatterbox/0.1 (+isitdown check)";

    private final HttpClient http;
    private final int requestTimeoutSeconds;

    IsItDownChecker() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(),
                REQUEST_TIMEOUT);
    }

    /** Test seam — lets tests pass a client pointed at a local server and shorten the timeout. */
    IsItDownChecker(HttpClient http, int requestTimeoutSeconds) {
        this.http = http;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    /**
     * Probes {@code uri}. Pre-condition: {@code uri} must be syntactically
     * valid and have already passed {@link UrlGuard#resolve}. Always returns
     * a result; never throws checked exceptions to the caller.
     */
    CheckResult check(URI uri) {
        String host = uri.getHost();
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                // Most servers honour Range and return only 1 KB; the rest get
                // capped client-side via MAX_RESPONSE_BYTES below.
                .header("Range", "bytes=0-" + (MAX_RESPONSE_BYTES - 1))
                .GET()
                .build();

        long startNs = System.nanoTime();
        HttpResponse<InputStream> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            return new CheckResult.Timeout(requestTimeoutSeconds);
        } catch (UnknownHostException e) {
            // Should be rare here — UrlGuard already resolved — but a stale DNS
            // cache or a host that became unresolvable between resolve and connect
            // can still surface this. Treat the same as the pre-flight DnsFailure.
            return new CheckResult.DnsFailure(host);
        } catch (ConnectException e) {
            return classifyConnect(e, host);
        } catch (NoRouteToHostException e) {
            return new CheckResult.Unreachable(host);
        } catch (SSLException e) {
            return new CheckResult.TlsError(host);
        } catch (IOException e) {
            log.warn("Unexpected I/O error probing {}: {}", host, e.toString());
            return new CheckResult.Other(host);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while probing {}", host);
            return new CheckResult.Other(host);
        } catch (RuntimeException e) {
            log.warn("Unexpected runtime error probing {}", host, e);
            return new CheckResult.Other(host);
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        try (InputStream body = resp.body()) {
            drainCapped(body);
        } catch (IOException e) {
            // Headers came back fine — body read failed mid-flight. Status is still
            // valid; we don't degrade the result on a body hiccup.
            log.debug("Body read for {} ended early: {}", host, e.toString());
        }

        int status = resp.statusCode();
        if (status >= 200 && status < 300) {
            return new CheckResult.Live(status, elapsedMs);
        }
        return new CheckResult.BadStatus(status, elapsedMs);
    }

    /**
     * "Connection refused" and "network unreachable" both arrive as
     * {@link ConnectException} with the kernel's text in the message.
     * Discriminate on the message so the user gets a more accurate
     * explanation; fall back to {@code Unreachable} when ambiguous.
     */
    private static CheckResult classifyConnect(ConnectException e, String host) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (msg.contains("refused")) return new CheckResult.ConnectionRefused(host);
        return new CheckResult.Unreachable(host);
    }

    /**
     * Reads up to {@link #MAX_RESPONSE_BYTES} from the body and discards them,
     * then closes the stream. We don't actually care about the content — just
     * confirming the response landed and bounding our own memory use.
     */
    private static void drainCapped(InputStream body) throws IOException {
        byte[] buf = new byte[1024];
        int total = 0;
        while (total < MAX_RESPONSE_BYTES) {
            int n = body.read(buf, 0, Math.min(buf.length, MAX_RESPONSE_BYTES - total));
            if (n < 0) break;
            total += n;
        }
    }
}
