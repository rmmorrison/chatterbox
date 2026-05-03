package ca.ryanmorrison.chatterbox.features.shortener;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * Best-effort OpenGraph / Twitter Card / HTML metadata scraper.
 *
 * <p>Used by the URL shortener at {@code /shorten} time to capture link
 * preview metadata from the target page so the public redirect page can
 * render a Discord-friendly preview without bouncing the crawler through
 * a 30x.
 *
 * <h2>SSRF protection</h2>
 * Each hop's hostname is resolved before the fetch and rejected if any
 * resolved address is loopback, site-local (RFC 1918), link-local,
 * any-local, multicast, or in the IPv6 unique-local range. Redirects are
 * followed manually (up to {@link #MAX_REDIRECTS} hops) so each new URL
 * is re-validated rather than trusting the HTTP client to be safe.
 *
 * <h2>Resource bounds</h2>
 * <ul>
 *   <li>3s connect / read timeout per hop.</li>
 *   <li>1 MiB maximum response body.</li>
 *   <li>Only {@code text/html} responses are parsed.</li>
 * </ul>
 *
 * Any failure (network, parse, unsafe target) returns an empty Optional —
 * shortening proceeds unaffected, the link just won't have a preview.
 */
final class OpenGraphScraper {

    private static final Logger log = LoggerFactory.getLogger(OpenGraphScraper.class);

    static final int MAX_REDIRECTS = 5;
    static final int CONNECT_TIMEOUT_MS = 3000;
    static final int MAX_BODY_BYTES = 1_048_576;
    static final String USER_AGENT =
            "Mozilla/5.0 (compatible; ChatterboxLinkPreview/1.0; +https://github.com/rmmorrison/chatterbox)";

    /** Soft caps so a megabyte of og:description doesn't end up on every redirect page. */
    static final int MAX_TITLE_LEN       = 256;
    static final int MAX_DESCRIPTION_LEN = 1024;
    static final int MAX_IMAGE_URL_LEN   = 2048;
    static final int MAX_SITE_NAME_LEN   = 256;

    Optional<OpenGraphMetadata> scrape(String url) {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!isPublicUrl(current)) {
                log.debug("Refusing to scrape non-public URL: {}", current);
                return Optional.empty();
            }
            Connection.Response response;
            try {
                response = Jsoup.connect(current)
                        .userAgent(USER_AGENT)
                        .timeout(CONNECT_TIMEOUT_MS)
                        .maxBodySize(MAX_BODY_BYTES)
                        .followRedirects(false)
                        .ignoreHttpErrors(true)
                        .ignoreContentType(true)
                        .execute();
            } catch (IOException e) {
                log.debug("OG scrape network failure for {}: {}", current, e.getMessage());
                return Optional.empty();
            }

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.header("Location");
                if (location == null || location.isBlank()) return Optional.empty();
                String next = resolve(current, location);
                if (next == null) return Optional.empty();
                current = next;
                continue;
            }
            if (status >= 400) return Optional.empty();

            String contentType = response.contentType();
            if (contentType == null || !contentType.toLowerCase().contains("html")) {
                return Optional.empty();
            }

            try {
                Document doc = response.parse();
                OpenGraphMetadata meta = extract(doc);
                return meta.isEmpty() ? Optional.empty() : Optional.of(meta);
            } catch (IOException e) {
                log.debug("OG scrape parse failure for {}: {}", current, e.getMessage());
                return Optional.empty();
            }
        }
        log.debug("OG scrape exhausted {} redirects from {}", MAX_REDIRECTS, url);
        return Optional.empty();
    }

    static OpenGraphMetadata extract(Document doc) {
        String title = pickContent(doc,
                "meta[property=og:title]",
                "meta[name=twitter:title]");
        if (title == null) {
            String docTitle = doc.title();
            if (docTitle != null && !docTitle.isBlank()) title = docTitle.trim();
        }

        String description = pickContent(doc,
                "meta[property=og:description]",
                "meta[name=twitter:description]",
                "meta[name=description]");

        String image = pickContent(doc,
                "meta[property=og:image]",
                "meta[property=og:image:url]",
                "meta[name=twitter:image]",
                "meta[name=twitter:image:src]");

        String siteName = pickContent(doc, "meta[property=og:site_name]");

        return new OpenGraphMetadata(
                truncate(title, MAX_TITLE_LEN),
                truncate(description, MAX_DESCRIPTION_LEN),
                truncate(image, MAX_IMAGE_URL_LEN),
                truncate(siteName, MAX_SITE_NAME_LEN));
    }

    private static String pickContent(Document doc, String... selectors) {
        for (String sel : selectors) {
            Element el = doc.selectFirst(sel);
            if (el == null) continue;
            String v = el.attr("content");
            if (v == null || v.isBlank()) continue;
            return v.trim();
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static String resolve(String base, String location) {
        try {
            return URI.create(base).resolve(location).toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Hostname is resolved and every returned address is checked. Reject if
     * any is in a non-public range — this defends against DNS rebinding
     * variants where a name resolves to multiple records.
     */
    static boolean isPublicUrl(String urlStr) {
        URI uri;
        try {
            uri = URI.create(urlStr);
        } catch (RuntimeException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) return false;
        String host = uri.getHost();
        if (host == null || host.isBlank()) return false;

        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return false;
        }
        for (InetAddress a : addrs) {
            if (isPrivate(a)) return false;
        }
        return true;
    }

    private static boolean isPrivate(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress()
                || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        // IPv6 unique-local (fc00::/7) — not covered by isSiteLocalAddress.
        byte[] bytes = a.getAddress();
        if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) return true;
        return false;
    }
}
