package ca.ryanmorrison.chatterbox.features.rss;

import ca.ryanmorrison.chatterbox.common.net.UrlGuard;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RssFetcherTest {

    private HttpServer server;
    private int port;
    private RssFetcher fetcher;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
        // The test server is on loopback, which UrlGuard denies outright, so
        // the deny check is fed a resolver reporting a public address. The
        // request still goes to loopback. Deny-list behaviour itself is covered
        // by refusesPrivateAddress below and by UrlGuardTest.
        fetcher = new RssFetcher(RssFetcherTest::publicAddress);
    }

    /**
     * Reports the loopback test server as a public address, and resolves
     * everything else for real. Faking <em>every</em> host would also whitelist
     * redirect targets and quietly disarm the check the redirect test exists to
     * prove.
     */
    private static InetAddress[] publicAddress(String host) {
        if (!"127.0.0.1".equals(host)) {
            return UrlGuard.systemResolver(host);
        }
        try {
            return new InetAddress[]{
                    InetAddress.getByAddress(host, new byte[]{93, (byte) 184, (byte) 216, 34})};
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private void serve(String path, int status, String contentType, String body) {
        server.createContext(path, ex -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", contentType);
            ex.sendResponseHeaders(status, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private static final String VALID_RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Example Feed</title>
                <link>https://example.com</link>
                <description>desc</description>
                <item>
                  <title>First post</title>
                  <link>https://example.com/1</link>
                  <guid>https://example.com/1</guid>
                  <pubDate>Mon, 01 Jan 2026 00:00:00 GMT</pubDate>
                  <description>Hello world</description>
                </item>
              </channel>
            </rss>
            """;

    private static final String VALID_ATOM = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Example</title>
              <id>urn:uuid:abc</id>
              <updated>2026-01-01T00:00:00Z</updated>
              <entry>
                <title>Atom entry</title>
                <id>urn:uuid:1</id>
                <updated>2026-01-01T00:00:00Z</updated>
                <summary>Short summary</summary>
              </entry>
            </feed>
            """;

    @Test
    void validatesValidRss() throws Exception {
        serve("/rss", 200, "application/rss+xml", VALID_RSS);
        RssFetcher.Validated v = fetcher.validate(url("/rss"));
        assertEquals("Example Feed", v.title());
        assertTrue(v.url().endsWith("/rss"));
    }

    @Test
    void validatesValidAtom() throws Exception {
        serve("/atom", 200, "application/atom+xml", VALID_ATOM);
        RssFetcher.Validated v = fetcher.validate(url("/atom"));
        assertEquals("Atom Example", v.title());
    }

    @Test
    void rejectsNonHttpScheme() {
        var ex = assertThrows(RssFetcher.FetchException.class,
                () -> fetcher.validate("ftp://example.com/feed"));
        assertTrue(ex.getMessage().contains("http"));
    }

    @Test
    void rejectsBlankUrl() {
        assertThrows(RssFetcher.FetchException.class, () -> fetcher.validate("   "));
    }

    @Test
    void rejects404() {
        serve("/missing", 404, "text/plain", "not found");
        var ex = assertThrows(RssFetcher.FetchException.class,
                () -> fetcher.validate(url("/missing")));
        assertTrue(ex.getMessage().contains("404"));
    }

    @Test
    void rejectsNonXmlPayload() {
        serve("/html", 200, "text/html", "<html><body>nope</body></html>");
        assertThrows(RssFetcher.FetchException.class, () -> fetcher.validate(url("/html")));
    }

    @Test
    void rejectsXmlWithoutFeedShape() {
        serve("/random-xml", 200, "application/xml",
                "<?xml version=\"1.0\"?><root><node/></root>");
        assertThrows(RssFetcher.FetchException.class, () -> fetcher.validate(url("/random-xml")));
    }

    @Test
    void rejectsFeedWithoutTitle() {
        String noTitle = """
                <?xml version="1.0"?>
                <rss version="2.0"><channel>
                  <link>x</link><description>y</description>
                </channel></rss>
                """;
        serve("/notitle", 200, "application/rss+xml", noTitle);
        var ex = assertThrows(RssFetcher.FetchException.class,
                () -> fetcher.validate(url("/notitle")));
        assertTrue(ex.getMessage().toLowerCase().contains("title"));
    }

    // ---- SSRF ----

    /**
     * The core SSRF regression: /rss add is reachable by any guild member, so
     * the fetcher must refuse private address space. Uses the real resolver, so
     * loopback is genuinely denied.
     */
    @Test
    void refusesPrivateAddress() {
        RssFetcher guarded = new RssFetcher();
        var ex = assertThrows(RssFetcher.FetchException.class,
                () -> guarded.validate(url("/rss")));
        assertTrue(ex.getMessage().contains("loopback"),
                () -> "expected the loopback deny reason, got: " + ex.getMessage());
    }

    @Test
    void refusesCloudMetadataAddress() {
        RssFetcher guarded = new RssFetcher();
        var ex = assertThrows(RssFetcher.FetchException.class,
                () -> guarded.validate("http://169.254.169.254/latest/meta-data/"));
        assertTrue(ex.getMessage().contains("link-local"),
                () -> "got: " + ex.getMessage());
    }

    /** The scheduler path must be guarded too, not just /rss add. */
    @Test
    void fetchAlsoRefusesPrivateAddress() {
        RssFetcher guarded = new RssFetcher();
        assertThrows(RssFetcher.FetchException.class, () -> guarded.fetch(url("/rss")));
    }

    @Test
    void redirectToBlockedAddressIsRefused() {
        server.createContext("/redirect", ex -> {
            ex.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data/");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        var ex = assertThrows(RssFetcher.FetchException.class,
                () -> fetcher.validate(url("/redirect")));
        assertTrue(ex.getMessage().contains("link-local"),
                () -> "expected the redirect target to be denied, got: " + ex.getMessage());
    }

    // ---- parser hardening ----

    @Test
    void rejectsDoctypeSoExternalEntitiesNeverResolve() {
        // XXE probe: if doctypes were allowed, the parser would try to read
        // /etc/passwd. Rejecting the document outright is the desired outcome.
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <rss version="2.0"><channel>
                  <title>&xxe;</title><link>x</link><description>y</description>
                </channel></rss>
                """;
        serve("/xxe", 200, "application/rss+xml", xxe);
        assertThrows(RssFetcher.FetchException.class, () -> fetcher.validate(url("/xxe")));
    }

    @Test
    void rejectsBodyOverTheSizeCap() {
        // Padding inside a comment keeps the document well-formed, so the only
        // thing that can reject it is the size cap.
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><!--");
        sb.append("x".repeat(RssFetcher.MAX_RESPONSE_BYTES + 1024));
        sb.append("--><rss version=\"2.0\"><channel><title>t</title></channel></rss>");
        serve("/huge", 200, "application/rss+xml", sb.toString());
        var ex = assertThrows(RssFetcher.FetchException.class,
                () -> fetcher.validate(url("/huge")));
        assertTrue(ex.getMessage().contains("larger than"), () -> "got: " + ex.getMessage());
    }

    @Test
    void fetchReturnsParsedFeed() throws Exception {
        serve("/rss2", 200, "application/rss+xml", VALID_RSS);
        var feed = fetcher.fetch(url("/rss2"));
        assertNotNull(feed);
        assertEquals(1, RssFetcher.entries(feed).size());
        assertEquals("First post", RssFetcher.entries(feed).get(0).getTitle());
    }
}
