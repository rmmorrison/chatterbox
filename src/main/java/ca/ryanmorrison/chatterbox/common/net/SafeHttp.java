package ca.ryanmorrison.chatterbox.common.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.function.Function;

/**
 * Sends a request and follows redirects <em>manually</em>, re-running
 * {@link UrlGuard} against every hop.
 *
 * <p>This exists because {@link HttpClient.Redirect#NORMAL} defeats an SSRF
 * deny-list entirely: the guard validates the URL the user supplied, but the
 * client then silently follows a {@code 302} to wherever the remote server
 * points — including {@code 169.254.169.254} and friends. Validating hop 0 and
 * then trusting the client to stay there is not a check at all.
 *
 * <p>Clients passed here must therefore be built with
 * {@link HttpClient.Redirect#NEVER}; otherwise the client follows redirects
 * internally and this class never sees them.
 *
 * <p><strong>Hop 0 is the caller's responsibility.</strong> This class
 * validates only the targets it is redirected to, so callers must run
 * {@link UrlGuard#parse} and {@link UrlGuard#resolve} on the initial URI
 * themselves. Keeping it that way avoids a redundant DNS lookup on the common
 * no-redirect path, where the caller has already resolved the host to decide
 * whether to send at all.
 */
public final class SafeHttp {

    /** Matches the redirect cap the JDK client applies by default. */
    public static final int MAX_REDIRECTS = 5;

    private SafeHttp() {}

    /**
     * Signals that a redirect target was refused by {@link UrlGuard}, carrying
     * the guard's user-safe reason.
     *
     * <p>Extends {@link IOException} so it flows through the same failure paths
     * callers already handle — but callers that distinguish "blocked" from
     * "network error" should catch this <em>before</em> their generic
     * {@code IOException} branch.
     */
    public static final class BlockedException extends IOException {
        public BlockedException(String reason) { super(reason); }
    }

    /**
     * Sends {@code request}, following up to {@link #MAX_REDIRECTS} redirects
     * and validating each target against {@link UrlGuard}.
     *
     * <p>Bodies of intermediate redirect responses are closed before the next
     * hop, so streaming body handlers don't leak connections.
     *
     * @throws BlockedException if a redirect target fails the guard, or if the
     *         redirect chain is malformed (missing/unparseable {@code Location},
     *         too many hops)
     */
    public static <T> HttpResponse<T> send(HttpClient http,
                                           HttpRequest request,
                                           HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        return send(http, request, handler, UrlGuard::systemResolver);
    }

    /**
     * Test seam: lets tests drive redirect-following against a loopback server
     * without the guard rejecting every hop. The resolver only feeds the deny
     * check — the connection still goes to the URI's real host.
     */
    public static <T> HttpResponse<T> send(HttpClient http,
                                           HttpRequest request,
                                           HttpResponse.BodyHandler<T> handler,
                                           Function<String, InetAddress[]> resolver)
            throws IOException, InterruptedException {

        HttpRequest current = request;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpResponse<T> response = http.send(current, handler);
            if (!isRedirect(response.statusCode())) {
                return response;
            }

            Optional<String> location = response.headers().firstValue("Location");
            closeQuietly(response.body());
            if (location.isEmpty() || location.get().isBlank()) {
                throw new BlockedException("server sent a redirect with no destination.");
            }

            URI target = resolveTarget(current.uri(), location.get());
            guard(target, resolver);
            // Copy method, headers, timeout, and version; only the target changes.
            current = HttpRequest.newBuilder(current, (name, value) -> true).uri(target).build();
        }
        throw new BlockedException("too many redirects (more than " + MAX_REDIRECTS + ").");
    }

    /**
     * 300 and 304 are excluded deliberately: neither carries a destination we
     * should chase (300 is a choice for the user agent, 304 is a cache hit).
     */
    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303
                || status == 307 || status == 308;
    }

    private static URI resolveTarget(URI base, String location) throws BlockedException {
        try {
            // Relative Locations are legal and common, so resolve against the
            // URI we actually requested rather than assuming an absolute one.
            return base.resolve(new URI(location.trim()));
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new BlockedException("server sent an unparseable redirect destination.");
        }
    }

    /** Runs both guard stages against a redirect target. */
    private static void guard(URI target, Function<String, InetAddress[]> resolver)
            throws BlockedException {
        UrlGuard.ParsedUrl parsed = UrlGuard.parse(target.toString());
        if (parsed instanceof UrlGuard.ParsedUrl.Rejected(String reason)) {
            throw new BlockedException("redirect to " + reason);
        }
        UrlGuard.ResolvedUrl resolved =
                UrlGuard.resolve(((UrlGuard.ParsedUrl.Ok) parsed).uri(), resolver);
        switch (resolved) {
            case UrlGuard.ResolvedUrl.Ok ok -> { }
            case UrlGuard.ResolvedUrl.DnsFailure(String host) ->
                    throw new BlockedException("redirect target " + host + " didn't resolve.");
            case UrlGuard.ResolvedUrl.Disallowed(String reason) ->
                    throw new BlockedException("redirect to a blocked address: " + reason);
        }
    }

    private static void closeQuietly(Object body) {
        if (body instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Nothing useful to do — we're discarding this hop's body anyway.
            }
        }
    }
}
