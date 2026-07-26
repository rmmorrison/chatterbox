package ca.ryanmorrison.chatterbox.common.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shutdown helper for the module-owned {@link HttpClient} instances.
 *
 * <p>Each of these owns a selector thread and an executor. They live for the
 * process, so leaving them open leaks nothing in practice — but closing them
 * on module stop keeps {@code onStop} honest and makes the modules reusable
 * from a test without accumulating threads.
 */
public final class HttpClients {

    private static final Logger log = LoggerFactory.getLogger(HttpClients.class);

    /** Brief grace period for in-flight requests before forcing the issue. */
    private static final Duration DRAIN = Duration.ofSeconds(2);

    private HttpClients() {}

    /**
     * Shuts {@code client} down without ever blocking for long.
     *
     * <p>Deliberately not {@link HttpClient#close()}: that waits for every
     * in-flight request to finish, and this runs from a shutdown hook where a
     * single slow upstream would stall the whole exit.
     */
    public static void closeQuietly(HttpClient client) {
        if (client == null) return;
        try {
            client.shutdown();
            if (!client.awaitTermination(DRAIN)) {
                client.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            client.shutdownNow();
        } catch (RuntimeException e) {
            log.debug("Ignoring error while closing an HTTP client: {}", e.toString());
        }
    }
}
