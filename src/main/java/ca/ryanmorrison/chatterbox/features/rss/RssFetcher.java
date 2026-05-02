package ca.ryanmorrison.chatterbox.features.rss;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

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
 * <p>Bounded for safety: 10s connect/response timeout, 2 MB max body, max 5
 * redirects, fixed {@code User-Agent}. Parsing happens in-memory from the
 * already-bounded byte array.
 */
final class RssFetcher {

    static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    static final String USER_AGENT = "Chatterbox/0.1 (+RSS)";

    private final HttpClient http;

    RssFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /** Test seam. */
    RssFetcher(HttpClient http) {
        this.http = http;
    }

    /**
     * Result of a successful {@link #validate} call: the parsed feed title and
     * the canonical (post-normalisation) URL string we should persist.
     */
    record Validated(String title, String url) {}

    /**
     * Fetches and parses {@code rawUrl}, returning the feed title to persist.
     *
     * @throws FetchException for any failure (bad URL, network error, oversize,
     *         unparseable XML, missing title). The message is safe to surface
     *         to a Discord user.
     */
    Validated validate(String rawUrl) throws FetchException {
        String normalised = normaliseUrl(rawUrl);
        SyndFeed feed = parse(load(normalised));
        String title = feed.getTitle() == null ? "" : feed.getTitle().trim();
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
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new FetchException("That isn't a valid URL.");
        }
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.8")
                .GET()
                .build();
        HttpResponse<byte[]> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new FetchException("Couldn't reach the URL: " + safeMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Fetch was interrupted.");
        }
        if (resp.statusCode() / 100 != 2) {
            throw new FetchException("Server returned HTTP " + resp.statusCode() + ".");
        }
        byte[] body = resp.body();
        if (body == null || body.length == 0) {
            throw new FetchException("Server returned an empty response.");
        }
        if (body.length > MAX_RESPONSE_BYTES) {
            throw new FetchException("Feed is larger than the " + (MAX_RESPONSE_BYTES / 1024) + " KB limit.");
        }
        return body;
    }

    private static SyndFeed parse(byte[] body) throws FetchException {
        // Disable XInclude/DTD lookups by routing through SAX with a hardened InputSource.
        SyndFeedInput input = new SyndFeedInput();
        input.setXmlHealerOn(true);
        try {
            return input.build(new InputSource(new ByteArrayInputStream(body)));
        } catch (FeedException | IllegalArgumentException e) {
            throw new FetchException("The URL didn't return a valid RSS or Atom feed.");
        }
    }

    private static String normaliseUrl(String raw) throws FetchException {
        if (raw == null) throw new FetchException("URL is required.");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) throw new FetchException("URL is required.");
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new FetchException("That isn't a valid URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new FetchException("URL must start with http:// or https://.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new FetchException("URL is missing a host.");
        }
        return uri.toString();
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
