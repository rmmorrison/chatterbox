package ca.ryanmorrison.chatterbox.features.rss;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
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
        fetcher = new RssFetcher();
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

    @Test
    void fetchReturnsParsedFeed() throws Exception {
        serve("/rss2", 200, "application/rss+xml", VALID_RSS);
        var feed = fetcher.fetch(url("/rss2"));
        assertNotNull(feed);
        assertEquals(1, RssFetcher.entries(feed).size());
        assertEquals("First post", RssFetcher.entries(feed).get(0).getTitle());
    }
}
