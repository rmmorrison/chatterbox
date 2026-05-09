package ca.ryanmorrison.chatterbox.features.wiki;

import ca.ryanmorrison.chatterbox.features.wiki.dto.PageSummary;
import ca.ryanmorrison.chatterbox.features.wiki.dto.SearchHit;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiClientTest {

    private HttpServer server;
    private WikiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        client = new WikiClient(http, baseUrl);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private void serve(String path, int status, String body) {
        server.createContext(path, ex -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
    }

    /** Trimmed real-shape Wikipedia summary response. */
    private static final String TORONTO_SUMMARY = """
            {
              "type": "standard",
              "title": "Toronto",
              "displaytitle": "Toronto",
              "description": "Capital city of Ontario, Canada",
              "extract": "Toronto is the capital city of the Canadian province of Ontario.",
              "thumbnail": {
                "source": "https://upload.wikimedia.org/wikipedia/commons/thumb/toronto.jpg",
                "width": 320, "height": 213
              },
              "content_urls": {
                "desktop": {
                  "page": "https://en.wikipedia.org/wiki/Toronto",
                  "revisions": "https://en.wikipedia.org/wiki/Toronto?action=history"
                },
                "mobile": {
                  "page": "https://en.m.wikipedia.org/wiki/Toronto"
                }
              }
            }
            """;

    private static final String DISAMBIGUATION_SUMMARY = """
            {
              "type": "disambiguation",
              "title": "Java",
              "extract": "Java may refer to:",
              "content_urls": {
                "desktop": {"page": "https://en.wikipedia.org/wiki/Java"}
              }
            }
            """;

    private static final String SEARCH_RESPONSE = """
            {
              "pages": [
                {"id": 64646, "key": "Toronto", "title": "Toronto",
                 "excerpt": "<span class=\\"searchmatch\\">Toronto</span> is..."},
                {"id": 1, "key": "Toronto_FC", "title": "Toronto FC"}
              ]
            }
            """;

    // ---- summary happy path ----

    @Test
    void summaryParsesStandardPage() throws Exception {
        serve("/api/rest_v1/page/summary/Toronto", 200, TORONTO_SUMMARY);
        Optional<PageSummary> got = client.summary("Toronto");
        assertTrue(got.isPresent());
        PageSummary p = got.get();
        assertEquals("Toronto", p.title());
        assertEquals("Capital city of Ontario, Canada", p.description());
        assertTrue(p.extract().startsWith("Toronto is the capital city"));
        assertEquals("https://en.wikipedia.org/wiki/Toronto", p.articleUrl().orElseThrow());
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/toronto.jpg",
                p.thumbnail().source());
        assertEquals(false, p.isDisambiguation());
    }

    @Test
    void summaryDetectsDisambiguation() throws Exception {
        serve("/api/rest_v1/page/summary/Java", 200, DISAMBIGUATION_SUMMARY);
        PageSummary p = client.summary("Java").orElseThrow();
        assertTrue(p.isDisambiguation());
        assertEquals("Java may refer to:", p.extract());
    }

    @Test
    void summaryWithSpacesIsPathEncoded() throws Exception {
        AtomicReference<String> rawPath = new AtomicReference<>();
        // Catch-all so we can capture the actual path.
        server.createContext("/api/rest_v1/page/summary/", ex -> {
            rawPath.set(ex.getRequestURI().getRawPath());
            byte[] bytes = TORONTO_SUMMARY.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        client.summary("Java (programming language)");
        assertEquals("/api/rest_v1/page/summary/Java%20%28programming%20language%29", rawPath.get(),
                "URL spaces must be %20 (paths), not + (form-encoding).");
    }

    // ---- summary failure paths ----

    @Test
    void summary404ReturnsEmpty() throws Exception {
        serve("/api/rest_v1/page/summary/Asdfqwer", 404,
                """
                {"type":"https://mediawiki.org/wiki/HyperSwitch/errors/not_found",
                 "title":"Not found.","detail":"Page or revision not found."}
                """);
        // 404 is the chain-into-search signal — must not throw.
        assertTrue(client.summary("Asdfqwer").isEmpty());
    }

    @Test
    void summary429SurfacesRateLimit() {
        serve("/api/rest_v1/page/summary/Toronto", 429, "rate limited");
        var ex = assertThrows(WikiClient.WikiException.class, () -> client.summary("Toronto"));
        assertTrue(ex.getMessage().toLowerCase().contains("rate"), () -> ex.getMessage());
    }

    @Test
    void summary500SurfacesGenericHttpStatus() {
        serve("/api/rest_v1/page/summary/Toronto", 500, "internal error");
        var ex = assertThrows(WikiClient.WikiException.class, () -> client.summary("Toronto"));
        assertTrue(ex.getMessage().contains("500"), () -> ex.getMessage());
    }

    @Test
    void summaryUnparseableBodySurfacedAsUnexpected() {
        serve("/api/rest_v1/page/summary/Toronto", 200, "{not json");
        var ex = assertThrows(WikiClient.WikiException.class, () -> client.summary("Toronto"));
        assertTrue(ex.getMessage().toLowerCase().contains("unexpected"), () -> ex.getMessage());
    }

    @Test
    void blankTitleRejectedClientSide() {
        assertThrows(WikiClient.WikiException.class, () -> client.summary(""));
        assertThrows(WikiClient.WikiException.class, () -> client.summary("   "));
        assertThrows(WikiClient.WikiException.class, () -> client.summary(null));
    }

    // ---- search ----

    @Test
    void searchHappyPathReturnsHits() throws Exception {
        serve("/w/rest.php/v1/search/page", 200, SEARCH_RESPONSE);
        List<SearchHit> hits = client.search("torono", 2);
        assertEquals(2, hits.size());
        assertEquals("Toronto", hits.get(0).title());
        assertEquals("Toronto", hits.get(0).key());
        assertEquals("Toronto FC", hits.get(1).title());
    }

    @Test
    void searchEmptyQueryReturnsEmpty() throws Exception {
        // Defensive — handler shouldn't pass blank, but if it did we shouldn't
        // make a wasted round-trip.
        assertEquals(List.of(), client.search("", 5));
        assertEquals(List.of(), client.search(null, 5));
    }

    @Test
    void searchEmptyResultsReturnsEmptyList() throws Exception {
        serve("/w/rest.php/v1/search/page", 200, """
                {"pages": []}
                """);
        assertEquals(List.of(), client.search("zzzzzznope", 1));
    }

    @Test
    void searchPassesQueryAndLimit() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server.createContext("/w/rest.php/v1/search/page", ex -> {
            rawQuery.set(ex.getRequestURI().getRawQuery());
            byte[] bytes = SEARCH_RESPONSE.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        client.search("torono", 1);
        assertTrue(rawQuery.get().contains("q=torono"), () -> "got: " + rawQuery.get());
        assertTrue(rawQuery.get().contains("limit=1"), () -> "got: " + rawQuery.get());
    }
}
