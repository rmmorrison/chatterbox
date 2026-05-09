package ca.ryanmorrison.chatterbox.features.weather;

import ca.ryanmorrison.chatterbox.features.weather.dto.WeatherResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherClientTest {

    private HttpServer server;
    private WeatherClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        client = new WeatherClient(http, baseUrl);
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

    /** Trimmed real-shape wttr.in response — verbatim shape, minimal data. */
    private static final String SAMPLE = """
            {
              "current_condition": [{
                "temp_C": "8", "temp_F": "47",
                "FeelsLikeC": "7", "FeelsLikeF": "44",
                "humidity": "81",
                "weatherCode": "116",
                "weatherDesc": [{"value": "Partly cloudy"}],
                "winddir16Point": "SSW",
                "windspeedKmph": "9", "windspeedMiles": "6",
                "uvIndex": "1",
                "pressure": "1006",
                "cloudcover": "75",
                "visibility": "14", "visibilityMiles": "8"
              }],
              "nearest_area": [{
                "areaName": [{"value": "Toronto"}],
                "region":   [{"value": "Ontario"}],
                "country":  [{"value": "Canada"}],
                "latitude": "43.667", "longitude": "-79.417"
              }],
              "weather": [
                {
                  "date": "2026-05-09",
                  "maxtempC": "12", "maxtempF": "54",
                  "mintempC": "5",  "mintempF": "41",
                  "avgtempC": "9",  "avgtempF": "48",
                  "uvIndex": "5",
                  "hourly": [
                    {"time": "1200", "tempC": "10", "tempF": "50",
                     "weatherCode": "116",
                     "weatherDesc": [{"value": "Partly cloudy"}]}
                  ]
                },
                {
                  "date": "2026-05-10",
                  "maxtempC": "15", "maxtempF": "59",
                  "mintempC": "7",  "mintempF": "45",
                  "hourly": [
                    {"time": "1200", "weatherCode": "308",
                     "weatherDesc": [{"value": "Heavy rain"}]}
                  ]
                },
                {
                  "date": "2026-05-11",
                  "maxtempC": "18", "maxtempF": "64",
                  "mintempC": "10", "mintempF": "50",
                  "hourly": [
                    {"time": "1200", "weatherCode": "113",
                     "weatherDesc": [{"value": "Sunny"}]}
                  ]
                }
              ]
            }
            """;

    // ---- happy path ----

    @Test
    void successParsesNestedShape() throws Exception {
        serve("/Toronto", 200, "application/json", SAMPLE);
        WeatherResponse resp = client.fetch("Toronto");

        assertTrue(resp.current().isPresent());
        assertEquals("8",  resp.current().get().tempC());
        assertEquals("Partly cloudy", resp.current().get().description());

        assertTrue(resp.nearest().isPresent());
        assertEquals("Toronto", resp.nearest().get().city().orElseThrow());
        assertEquals("Ontario", resp.nearest().get().regionName().orElseThrow());
        assertEquals("Canada",  resp.nearest().get().countryName().orElseThrow());

        assertEquals(3, resp.forecast().size());
        assertEquals("2026-05-09", resp.forecast().get(0).date());
        assertEquals("Heavy rain", resp.forecast().get(1).noonish().orElseThrow().description());
    }

    @Test
    void formatJ1QueryAndUserAgentAreSent() throws Exception {
        // Capture the actual request-line+headers wttr.in sees.
        java.util.concurrent.atomic.AtomicReference<String> rawQuery = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> userAgent = new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/Toronto", ex -> {
            rawQuery.set(ex.getRequestURI().getRawQuery());
            userAgent.set(ex.getRequestHeaders().getFirst("User-Agent"));
            byte[] bytes = SAMPLE.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        client.fetch("Toronto");
        assertEquals("format=j1", rawQuery.get(),
                "wttr.in returns ANSI/HTML by default; we must pin format=j1 to get JSON.");
        assertNotNull(userAgent.get());
    }

    @Test
    void locationWithSpacesIsUrlEncodedAsPath() throws Exception {
        // Capture what path the client actually sends so we can assert %20 not +.
        java.util.concurrent.atomic.AtomicReference<String> rawPath = new java.util.concurrent.atomic.AtomicReference<>();
        // Catch-all context at root: HttpServer routes longest-prefix-first.
        server.createContext("/", ex -> {
            rawPath.set(ex.getRequestURI().getRawPath());
            byte[] bytes = SAMPLE.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        WeatherResponse resp = client.fetch("London UK");
        assertTrue(resp.current().isPresent());
        // %20, not + (which would be form-encoding, wrong for path components).
        assertEquals("/London%20UK", rawPath.get());
    }

    // ---- failure paths ----

    @Test
    void blankLocationRejectedClientSide() {
        // We never let an empty path through — wttr.in would interpret it as
        // "use my IP" and return weather for the bot's datacentre.
        var ex = assertThrows(WeatherClient.WeatherException.class, () -> client.fetch(""));
        assertTrue(ex.getMessage().toLowerCase().contains("location"), () -> ex.getMessage());
        assertThrows(WeatherClient.WeatherException.class, () -> client.fetch(null));
        assertThrows(WeatherClient.WeatherException.class, () -> client.fetch("   "));
    }

    @Test
    void unknownLocationDetectedFromBodySniff() {
        // wttr.in returns 500 with this body for typos; we surface a friendly
        // "couldn't find" rather than the noisy "HTTP 500".
        serve("/asdfqwer", 500, "text/plain",
                "location not found: upstream error: opencage: invalid response\n");
        var ex = assertThrows(WeatherClient.WeatherException.class, () -> client.fetch("asdfqwer"));
        assertTrue(ex.getMessage().toLowerCase().contains("couldn't find"),
                () -> ex.getMessage());
    }

    @Test
    void rateLimitedReturnsFriendlyMessage() {
        serve("/Toronto", 429, "text/plain", "rate limited");
        var ex = assertThrows(WeatherClient.WeatherException.class, () -> client.fetch("Toronto"));
        assertTrue(ex.getMessage().toLowerCase().contains("rate"), () -> ex.getMessage());
    }

    @Test
    void otherServerErrorBubblesGenericHttpStatus() {
        serve("/Toronto", 503, "text/plain", "service unavailable");
        var ex = assertThrows(WeatherClient.WeatherException.class, () -> client.fetch("Toronto"));
        assertTrue(ex.getMessage().contains("503"), () -> ex.getMessage());
    }

    @Test
    void unparseableBodySurfacedAsUnexpected() {
        serve("/Toronto", 200, "application/json", "{not json");
        var ex = assertThrows(WeatherClient.WeatherException.class, () -> client.fetch("Toronto"));
        assertTrue(ex.getMessage().toLowerCase().contains("unexpected"), () -> ex.getMessage());
    }
}
