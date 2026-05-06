package ca.ryanmorrison.chatterbox.features.frinkiac;

import ca.ryanmorrison.chatterbox.features.frinkiac.dto.CaptionResponse;
import ca.ryanmorrison.chatterbox.features.frinkiac.dto.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrinkiacClientTest {

    private HttpServer server;
    private FrinkiacClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        client = new FrinkiacClient(http, baseUrl);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private void serveJson(String path, int status, String body) {
        server.createContext(path, ex -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
    }

    private void serveBytes(String path, int status, byte[] body, String contentType,
                            AtomicReference<String> capturedQuery) {
        server.createContext(path, ex -> {
            if (capturedQuery != null) {
                capturedQuery.set(ex.getRequestURI().getRawQuery());
            }
            ex.getResponseHeaders().add("Content-Type", contentType);
            ex.sendResponseHeaders(status, body.length);
            try (var os = ex.getResponseBody()) { os.write(body); }
        });
    }

    @Test
    void searchParsesHits() throws Exception {
        serveJson("/api/search", 200, """
                [
                  {"Id":1,"Episode":"S04E12","Timestamp":1279570,"Content":"Donuts.","Title":"Marge vs. the Monorail"},
                  {"Id":2,"Episode":"S37E05","Timestamp":953827,"Content":"to teach my wife how to make donuts.","Title":"Bad Boys... for Life?"}
                ]
                """);
        List<SearchResult> hits = client.search("donuts");
        assertEquals(2, hits.size());
        assertEquals("S04E12", hits.get(0).episode());
        assertEquals(1279570L, hits.get(0).timestamp());
        assertEquals("Donuts.", hits.get(0).content());
        assertEquals("Marge vs. the Monorail", hits.get(0).title());
    }

    @Test
    void blankSearchRejected() {
        var ex = assertThrows(FrinkiacClient.FrinkiacException.class, () -> client.search("   "));
        assertTrue(ex.getMessage().toLowerCase().contains("query"), () -> ex.getMessage());
    }

    @Test
    void captionParsesEpisodeAndSubtitles() throws Exception {
        serveJson("/api/caption", 200, """
                {
                  "Episode": {"Id":406,"Key":"S04E12","Season":4,"EpisodeNumber":12,
                              "Title":"Marge vs. the Monorail","Director":"Rich Moore",
                              "Writer":"Conan O'Brien","OriginalAirDate":"1993-01-14",
                              "WikiLink":"https://en.wikipedia.org/wiki/Marge_vs._the_Monorail"},
                  "Frame": {"Id":3493747,"Episode":"S04E12","Timestamp":1279570},
                  "Subtitles": [
                    {"Id":1,"RepresentativeTimestamp":1276025,"Episode":"S04E12",
                     "StartTimestamp":1275524,"EndTimestamp":1276984,"Content":"Huh?","Language":"en"},
                    {"Id":2,"RepresentativeTimestamp":1278736,"Episode":"S04E12",
                     "StartTimestamp":1278193,"EndTimestamp":1279736,"Content":"Donuts.","Language":"en"}
                  ],
                  "MinTimestamp": 1001, "MaxTimestamp": 1384258
                }
                """);
        CaptionResponse cap = client.caption("S04E12", 1279570);
        assertEquals("S04E12", cap.episode().key());
        assertEquals("Marge vs. the Monorail", cap.episode().title());
        assertEquals(2, cap.subtitles().size());
        assertEquals("Donuts.", cap.subtitles().get(1).content());
    }

    @Test
    void fetchCaptionedFrameSendsBase64PanelJson() throws Exception {
        byte[] fake = new byte[]{(byte) 0xFF, (byte) 0xD8, 9, 9, 9};
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        serveBytes("/comic/img", 200, fake, "image/jpeg", capturedQuery);

        // Caption chosen so its standard-base64 form contains a "/" — this
        // forces the URL-safe-encoder check below to actually have bite.
        byte[] got = client.fetchCaptionedFrame("S04E12", 1279570,
                "Hello? Hellodilly-odilly?\nworld");
        assertArrayEquals(fake, got);

        // Decode the b64 query and check the panel structure round-trips through Jackson.
        String q = capturedQuery.get();
        assertNotNull(q, "expected /comic/img request to carry a query string");
        assertTrue(q.startsWith("b64="), () -> "got: " + q);
        String b64 = java.net.URLDecoder.decode(q.substring("b64=".length()), StandardCharsets.UTF_8);
        // URL-safe alphabet — the renderer rejects standard base64 ("+/").
        assertTrue(!b64.contains("+") && !b64.contains("/"),
                () -> "expected URL-safe base64, got: " + b64);
        byte[] json = Base64.getUrlDecoder().decode(b64);
        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = mapper.readValue(json, List.class);
        assertEquals(1, panels.size(), "expected single-panel payload");

        Map<String, Object> panel = panels.get(0);
        assertEquals("S04E12", panel.get("e"));
        assertEquals(1279570, ((Number) panel.get("ts")).longValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> overlays = (List<Map<String, Object>>) panel.get("o");
        assertEquals(1, overlays.size());
        // Overlay uses the compact, single-letter-keyed shape the renderer expects.
        // Verbose keys (text/font/size/...) are silently ignored and produce an uncaptioned image.
        Map<String, Object> overlay = overlays.get(0);
        assertEquals("Hello? Hellodilly-odilly?\nworld", overlay.get("t"));
        assertEquals(CaptionOverlay.DEFAULT_FONT, overlay.get("f"));
        assertEquals(0, ((Number) overlay.get("s")).intValue());
        assertEquals("ffffffff", overlay.get("c"));
        assertEquals(50, ((Number) overlay.get("x")).intValue());
        assertEquals(97, ((Number) overlay.get("y")).intValue());
        assertEquals("c", overlay.get("a"));
        // Optional fields (b/d/u) are omitted when unset.
        assertTrue(!overlay.containsKey("b") && !overlay.containsKey("d") && !overlay.containsKey("u"),
                () -> "expected optional fields to be omitted: " + overlay.keySet());
    }

    @Test
    void serverErrorBubblesThroughWithStatusInMessage() {
        serveJson("/api/search", 503, "down");
        var ex = assertThrows(FrinkiacClient.FrinkiacException.class, () -> client.search("anything"));
        assertTrue(ex.getMessage().contains("503"), () -> ex.getMessage());
    }

    @Test
    void unparseableSearchBodyIsReportedAsUnexpected() {
        serveJson("/api/search", 200, "{not json");
        var ex = assertThrows(FrinkiacClient.FrinkiacException.class, () -> client.search("anything"));
        assertTrue(ex.getMessage().toLowerCase().contains("unexpected"), () -> ex.getMessage());
    }
}
