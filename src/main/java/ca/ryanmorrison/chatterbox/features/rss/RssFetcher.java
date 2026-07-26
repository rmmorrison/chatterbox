package ca.ryanmorrison.chatterbox.features.rss;

import ca.ryanmorrison.chatterbox.common.net.BoundedBody;
import ca.ryanmorrison.chatterbox.common.net.SafeHttp;
import ca.ryanmorrison.chatterbox.common.net.UrlGuard;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import org.jsoup.parser.Parser;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Loads and parses an RSS or Atom feed using the JDK HTTP client and Rome.
 *
 * <p>Two responsibilities, both kept narrow:
 * <ul>
 *   <li>{@link #validate} — used at {@code /rss add} time to confirm that a
 *       URL is reachable and produces a parseable feed with a non-blank title.
 *   <li>{@link #fetch} — used by the scheduler each refresh tick.
 * </ul>
 *
 * <p>Bounded for safety: 10s connect/response timeout, 2 MB max body enforced
 * <em>while streaming</em>, max 5 redirects, fixed {@code User-Agent}.
 *
 * <h2>SSRF</h2>
 * The URL here is supplied by a Discord user, so every fetch runs through
 * {@link UrlGuard} — on the initial URL and, via {@link SafeHttp}, on every
 * redirect hop. The guard runs on {@link #fetch} as well as {@link #validate}:
 * a host that was public when the feed was added can start resolving to a
 * private address later, and the scheduler would otherwise keep fetching it
 * every refresh tick.
 */
final class RssFetcher {

    static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    static final String USER_AGENT = "Chatterbox/0.1 (+RSS)";

    private final HttpClient http;
    private final Function<String, InetAddress[]> resolver;

    RssFetcher() {
        this(defaultClient(), UrlGuard::systemResolver);
    }

    /**
     * Test seam — substitutes the resolver backing the SSRF deny check so tests
     * can serve feeds from a loopback server. The connection still goes to the
     * URL's real host; only the deny check consults this.
     */
    RssFetcher(Function<String, InetAddress[]> resolver) {
        this(defaultClient(), resolver);
    }

    /** Test seam. */
    RssFetcher(HttpClient http) {
        this(http, UrlGuard::systemResolver);
    }

    RssFetcher(HttpClient http, Function<String, InetAddress[]> resolver) {
        this.http = http;
        this.resolver = resolver;
    }

    private static HttpClient defaultClient() {
        // NEVER, not NORMAL: SafeHttp follows redirects itself so each hop can
        // be re-checked against UrlGuard. With NORMAL the client would chase a
        // 302 into private address space without asking.
        return HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Result of a successful {@link #validate} call: the parsed feed title and
     * the canonical (post-normalisation) URL string we should persist.
     */
    record Validated(String title, String url) {}

    /**
     * Fetches and parses {@code rawUrl}, returning the feed title to persist.
     *
     * @throws FetchException for any failure (bad URL, blocked address, network
     *         error, oversize, unparseable XML, missing title). The message is
     *         safe to surface to a Discord user.
     */
    Validated validate(String rawUrl) throws FetchException {
        String normalised = normaliseUrl(rawUrl);
        SyndFeed feed = parse(load(normalised));
        String rawTitle = feed.getTitle() == null ? "" : feed.getTitle();
        String title = Parser.unescapeEntities(rawTitle, false).trim();
        if (title.isEmpty()) {
            throw new FetchException("The feed parsed successfully but has no title.");
        }
        return new Validated(title, normalised);
    }

    /** Fetches and parses {@code url}, returning the parsed feed. */
    SyndFeed fetch(String url) throws FetchException {
        return parse(load(url));
    }

    /** Items in the order Rome returns them (which is the order of the source document). */
    static List<SyndEntry> entries(SyndFeed feed) {
        return feed.getEntries() == null ? List.of() : feed.getEntries();
    }

    // ---- internals ----

    private byte[] load(String url) throws FetchException {
        URI uri = guard(url);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.8")
                .GET()
                .build();
        HttpResponse<InputStream> resp;
        try {
            resp = SafeHttp.send(http, req, HttpResponse.BodyHandlers.ofInputStream(), resolver);
        } catch (SafeHttp.BlockedException e) {
            throw new FetchException("I won't fetch that: " + e.getMessage());
        } catch (IOException e) {
            throw new FetchException("Couldn't reach the URL: " + safeMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Fetch was interrupted.");
        }

        if (resp.statusCode() / 100 != 2) {
            closeQuietly(resp.body());
            throw new FetchException("Server returned HTTP " + resp.statusCode() + ".");
        }

        byte[] body;
        try (InputStream in = resp.body()) {
            body = BoundedBody.read(in, MAX_RESPONSE_BYTES);
        } catch (BoundedBody.TooLargeException e) {
            throw new FetchException("Feed is larger than the " + e.maxKilobytes() + " KB limit.");
        } catch (IOException e) {
            throw new FetchException("Couldn't read the feed: " + safeMessage(e));
        }
        if (body.length == 0) {
            throw new FetchException("Server returned an empty response.");
        }
        return body;
    }

    private static SyndFeed parse(byte[] body) throws FetchException {
        // Doctypes off means no DTD, so no external entities and no billion
        // laughs. Rome already defaults allowDoctypes to false; setting it here
        // pins the behaviour to our code rather than to a library default a
        // future upgrade could flip. Do not remove: this parser is pointed at
        // whatever host a Discord user names.
        SyndFeedInput input = new SyndFeedInput(false, Locale.ROOT);
        input.setAllowDoctypes(false);
        input.setXmlHealerOn(true);
        try {
            return input.build(new InputSource(new ByteArrayInputStream(body)));
        } catch (FeedException | IllegalArgumentException e) {
            throw new FetchException("The URL didn't return a valid RSS or Atom feed.");
        }
    }

    /** Syntactic normalisation only; the address check happens in {@link #guard}. */
    private static String normaliseUrl(String raw) throws FetchException {
        return guardParse(raw).toString();
    }

    /** Parses and address-checks {@code url}, returning the URI to request. */
    private URI guard(String url) throws FetchException {
        URI uri = guardParse(url);
        UrlGuard.ResolvedUrl resolved = UrlGuard.resolve(uri, resolver);
        switch (resolved) {
            case UrlGuard.ResolvedUrl.Ok ok -> { }
            case UrlGuard.ResolvedUrl.DnsFailure(String host) ->
                    throw new FetchException("Couldn't resolve " + host + ".");
            case UrlGuard.ResolvedUrl.Disallowed(String reason) ->
                    throw new FetchException("I won't fetch that address: " + reason);
        }
        return uri;
    }

    private static URI guardParse(String url) throws FetchException {
        UrlGuard.ParsedUrl parsed = UrlGuard.parse(url);
        if (parsed instanceof UrlGuard.ParsedUrl.Rejected(String reason)) {
            throw new FetchException("That isn't a usable feed URL — " + reason);
        }
        return ((UrlGuard.ParsedUrl.Ok) parsed).uri();
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException e) {
            // Discarding this body anyway; nothing useful to do.
        }
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return (msg == null || msg.isBlank()) ? t.getClass().getSimpleName() : msg;
    }

    /** User-safe checked exception for any fetch/parse failure. */
    static final class FetchException extends Exception {
        FetchException(String message) { super(message); }
    }
}
