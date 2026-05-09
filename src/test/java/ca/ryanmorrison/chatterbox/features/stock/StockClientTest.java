package ca.ryanmorrison.chatterbox.features.stock;

import ca.ryanmorrison.chatterbox.features.stock.dto.ChartMeta;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockClientTest {

    private HttpServer server;
    private StockClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        client = new StockClient(http, baseUrl);
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

    /** Captured-shape NASDAQ response (AAPL). */
    private static final String AAPL_OK = """
            {"chart":{"result":[{"meta":{
              "currency":"USD","symbol":"AAPL",
              "exchangeName":"NMS","fullExchangeName":"NasdaqGS",
              "regularMarketTime":1778270402,
              "regularMarketPrice":293.32,
              "fiftyTwoWeekHigh":294.76,"fiftyTwoWeekLow":193.46,
              "regularMarketDayHigh":294.76,"regularMarketDayLow":290.0,
              "regularMarketVolume":45708423,
              "longName":"Apple Inc.","shortName":"Apple Inc.",
              "chartPreviousClose":287.423
            }}],"error":null}}
            """;

    /** Captured-shape TSX response (SHOP.TO). */
    private static final String SHOP_TO_OK = """
            {"chart":{"result":[{"meta":{
              "currency":"CAD","symbol":"SHOP.TO",
              "exchangeName":"TOR","fullExchangeName":"Toronto",
              "regularMarketTime":1778270402,
              "regularMarketPrice":150.68,
              "chartPreviousClose":152.57,
              "longName":"Shopify Inc."
            }}],"error":null}}
            """;

    private static final String NOT_FOUND = """
            {"chart":{"result":null,"error":{
              "code":"Not Found",
              "description":"No data found, symbol may be delisted"
            }}}
            """;

    // ---- happy paths ----

    @Test
    void parsesNasdaqResponse() throws Exception {
        serve("/v8/finance/chart/AAPL", 200, AAPL_OK);
        ChartMeta meta = client.fetch("AAPL");
        assertEquals("AAPL", meta.symbol());
        assertEquals("Apple Inc.", meta.longName());
        assertEquals("USD", meta.currency());
        assertEquals("NasdaqGS", meta.fullExchangeName());
        assertEquals(293.32, meta.regularMarketPrice(), 0.0001);
        assertEquals(287.423, meta.chartPreviousClose(), 0.0001);
        assertEquals(45_708_423L, meta.regularMarketVolume());
    }

    @Test
    void parsesTsxResponse() throws Exception {
        serve("/v8/finance/chart/SHOP.TO", 200, SHOP_TO_OK);
        ChartMeta meta = client.fetch("SHOP.TO");
        assertEquals("SHOP.TO", meta.symbol());
        assertEquals("CAD", meta.currency());
        assertEquals("Toronto", meta.fullExchangeName());
        assertEquals(150.68, meta.regularMarketPrice(), 0.0001);
    }

    @Test
    void displayNameFallsBackThroughLongShortAndSymbol() {
        // longName present
        ChartMeta a = new ChartMeta("AAPL", "Apple Inc.", "Apple", "USD", "NMS", "NasdaqGS",
                1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1L, 1L);
        assertEquals("Apple Inc.", a.displayName());
        // long blank, short present
        ChartMeta b = new ChartMeta("AAPL", "", "Apple", "USD", "NMS", "NasdaqGS",
                1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1L, 1L);
        assertEquals("Apple", b.displayName());
        // both blank — symbol
        ChartMeta c = new ChartMeta("AAPL", null, null, "USD", "NMS", "NasdaqGS",
                1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1L, 1L);
        assertEquals("AAPL", c.displayName());
    }

    // ---- failure paths ----

    @Test
    void notFoundSurfacedWithDelistedHintWhenYahooSaysSo() {
        serve("/v8/finance/chart/ZZZ", 404, NOT_FOUND);
        var ex = assertThrows(StockClient.StockException.class, () -> client.fetch("ZZZ"));
        assertTrue(ex.getMessage().toLowerCase().contains("delisted"),
                () -> "expected delisted hint, got: " + ex.getMessage());
    }

    @Test
    void notFoundFallsBackToFormatHintWhenBodyShapeChanged() {
        // 404 with no parseable error block — we still produce a typo prompt
        // rather than a noisy "HTTP 404".
        serve("/v8/finance/chart/ZZZ", 404, "<!doctype html>not json");
        var ex = assertThrows(StockClient.StockException.class, () -> client.fetch("ZZZ"));
        assertTrue(ex.getMessage().toLowerCase().contains("couldn't find"), () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("AAPL"),
                () -> "expected format hint with examples, got: " + ex.getMessage());
    }

    @Test
    void notFoundShapeIn200StillTreatedAsNotFound() {
        // Yahoo occasionally returns 200 with a populated error block.
        serve("/v8/finance/chart/ZZZ", 200, NOT_FOUND);
        var ex = assertThrows(StockClient.StockException.class, () -> client.fetch("ZZZ"));
        assertTrue(ex.getMessage().toLowerCase().contains("delisted"),
                () -> ex.getMessage());
    }

    @Test
    void rateLimitSurfacesFriendlyMessage() {
        serve("/v8/finance/chart/AAPL", 429, "");
        var ex = assertThrows(StockClient.StockException.class, () -> client.fetch("AAPL"));
        assertTrue(ex.getMessage().toLowerCase().contains("rate"), () -> ex.getMessage());
    }

    @Test
    void otherServerErrorBubblesGenericHttpStatus() {
        serve("/v8/finance/chart/AAPL", 503, "service unavailable");
        var ex = assertThrows(StockClient.StockException.class, () -> client.fetch("AAPL"));
        assertTrue(ex.getMessage().contains("503"), () -> ex.getMessage());
    }

    @Test
    void unparseableBodySurfacedAsUnexpected() {
        serve("/v8/finance/chart/AAPL", 200, "{not json");
        var ex = assertThrows(StockClient.StockException.class, () -> client.fetch("AAPL"));
        assertTrue(ex.getMessage().toLowerCase().contains("unexpected"), () -> ex.getMessage());
    }

    @Test
    void blankSymbolRejectedClientSide() {
        assertThrows(StockClient.StockException.class, () -> client.fetch(""));
        assertThrows(StockClient.StockException.class, () -> client.fetch("   "));
        assertThrows(StockClient.StockException.class, () -> client.fetch(null));
    }

    // ---- request shape ----

    @Test
    void browserUserAgentSentSinceYahooBlocksBotShapedUAs() throws Exception {
        AtomicReference<String> ua = new AtomicReference<>();
        server.createContext("/v8/finance/chart/AAPL", ex -> {
            ua.set(ex.getRequestHeaders().getFirst("User-Agent"));
            byte[] bytes = AAPL_OK.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        client.fetch("AAPL");
        assertNotNull(ua.get());
        assertTrue(ua.get().toLowerCase().contains("mozilla") || ua.get().toLowerCase().contains("chrome"),
                () -> "expected browser-shaped UA, got: " + ua.get());
    }

    @Test
    void intervalAndRangeQueryParamsAreSet() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server.createContext("/v8/finance/chart/AAPL", ex -> {
            rawQuery.set(ex.getRequestURI().getRawQuery());
            byte[] bytes = AAPL_OK.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        client.fetch("AAPL");
        assertEquals("interval=1d&range=1d", rawQuery.get());
    }
}
