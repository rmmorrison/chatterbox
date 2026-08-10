package ca.ryanmorrison.chatterbox.features.trivia;

import ca.ryanmorrison.chatterbox.features.trivia.dto.CategoryListResponse;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriviaCategoryCacheTest {

    private HttpServer server;
    private TriviaClient client;
    private final AtomicInteger requestCount = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        client = new TriviaClient(http, baseUrl);
        requestCount.set(0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private void serveJson(int status, String body) {
        server.createContext("/api_category.php", ex -> {
            requestCount.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
    }

    private static final String SAMPLE = """
            {"trivia_categories": [
                {"id": 9, "name": "General Knowledge"},
                {"id": 22, "name": "Geography"},
                {"id": 18, "name": "Science: Computers"}
            ]}
            """;

    @Test
    void firstAccessFetches_subsequentAccessesAreCached() {
        serveJson(200, SAMPLE);
        var cache = new TriviaCategoryCache(client);

        List<CategoryListResponse.Category> first = cache.categories();
        assertEquals(3, first.size());
        assertEquals("Geography", first.get(1).name());
        assertEquals(1, requestCount.get(), "first call triggers exactly one fetch");

        // Second access: served from cache, no extra HTTP call.
        var second = cache.categories();
        assertEquals(first, second);
        assertEquals(1, requestCount.get(), "subsequent calls must not re-fetch");
    }

    @Test
    void nameForResolvesById() {
        serveJson(200, SAMPLE);
        var cache = new TriviaCategoryCache(client);
        assertEquals("General Knowledge", cache.nameFor(9).orElseThrow());
        assertEquals("Geography", cache.nameFor(22).orElseThrow());
        assertTrue(cache.nameFor(99999).isEmpty(),
                "unknown id should return empty so the embed falls back");
    }

    @Test
    void failedFetchReturnsEmptyAndIsRetriedOnNextCall() {
        // First response is a 503; second is a normal payload. Since the
        // cache doesn't memoise failures, the second call should succeed.
        var firstFailed = new java.util.concurrent.atomic.AtomicBoolean(false);
        server.createContext("/api_category.php", ex -> {
            requestCount.incrementAndGet();
            if (!firstFailed.getAndSet(true)) {
                byte[] bytes = "down".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(503, bytes.length);
                try (var os = ex.getResponseBody()) { os.write(bytes); }
            } else {
                byte[] bytes = SAMPLE.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, bytes.length);
                try (var os = ex.getResponseBody()) { os.write(bytes); }
            }
        });

        var cache = new TriviaCategoryCache(client);
        assertTrue(cache.categories().isEmpty(),
                "fetch failure should surface as an empty list, not a thrown exception");
        // Second attempt — should retry and succeed.
        assertEquals(3, cache.categories().size());
        assertEquals(2, requestCount.get(), "two HTTP attempts: one failure, one success");
    }
}
