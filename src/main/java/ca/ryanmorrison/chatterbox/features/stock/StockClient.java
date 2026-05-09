package ca.ryanmorrison.chatterbox.features.stock;

import ca.ryanmorrison.chatterbox.features.stock.dto.ChartMeta;
import ca.ryanmorrison.chatterbox.features.stock.dto.ChartResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Talks to Yahoo Finance's {@code v8/finance/chart} endpoint.
 *
 * <h2>Caveats</h2>
 * <ul>
 *   <li>This endpoint is not officially documented and Yahoo could change
 *       or block it without notice. Widely used and stable for years; if it
 *       breaks, swapping to a key-based provider (Alpha Vantage, Finnhub)
 *       is a single-file change.</li>
 *   <li>Yahoo blanket-403s bot-flavoured user-agents — same WAF behaviour
 *       we hit on {@code /isitdown}. We send a Chrome-shaped UA to get
 *       through, with a TODO to bump it when sniffing tools start flagging
 *       the version as old.</li>
 *   <li>Free quotes are exchange-delayed (typically 15 minutes). The embed
 *       footer notes this so callers aren't surprised.</li>
 * </ul>
 *
 * <p>Posture mirrors {@code NhlClient} / {@code WeatherClient}: 10s timeout,
 * 1 MB body cap, all failures funnelled into {@link StockException} with
 * messages safe to surface in a Discord reply.
 */
final class StockClient {

    static final String BASE_URL = "https://query1.finance.yahoo.com";
    static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper mapper;

    StockClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                BASE_URL);
    }

    /** Test seam — lets tests point at a local {@code HttpServer}. */
    StockClient(HttpClient http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.mapper = JsonMapper.builder()
                .findAndAddModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /** Fetch the meta block for {@code symbol}. Throws {@link StockException} on any failure. */
    ChartMeta fetch(String symbol) throws StockException {
        if (symbol == null || symbol.isBlank()) {
            throw new StockException("Tell me a stock symbol.");
        }
        // URLEncoder uses form-encoding (+ for space); paths need %20. Symbols
        // shouldn't contain spaces, but be defensive — Yahoo treats `BRK B`
        // and `BRK-B` differently, and we don't want a stray space silently
        // changing the meaning.
        String trimmed = symbol.trim();
        String encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8).replace("+", "%20");
        URI uri;
        try {
            uri = URI.create(baseUrl + "/v8/finance/chart/" + encoded + "?interval=1d&range=1d");
        } catch (IllegalArgumentException e) {
            throw new StockException("That doesn't look like a valid symbol.");
        }
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<byte[]> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw new StockException("Yahoo Finance didn't respond within "
                    + HTTP_TIMEOUT.toSeconds() + " seconds.");
        } catch (IOException e) {
            throw new StockException("Couldn't reach Yahoo Finance.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StockException("Request was interrupted.");
        }
        int status = resp.statusCode();
        byte[] body = resp.body() == null ? new byte[0] : resp.body();
        if (body.length > MAX_RESPONSE_BYTES) {
            throw new StockException("Yahoo Finance response was too large.");
        }

        if (status == 429) {
            throw new StockException("Yahoo Finance is rate-limiting us. Try again in a minute.");
        }
        // Yahoo returns 404 with a structured JSON error for unknown symbols.
        // Try to parse it for a richer message; fall back to a generic typo
        // prompt if the body shape changed.
        if (status == 404) {
            throw notFoundException(trimmed, body);
        }
        if (status / 100 != 2) {
            throw new StockException("Yahoo Finance returned HTTP " + status + ".");
        }
        if (body.length == 0) {
            throw new StockException("Yahoo Finance returned an empty response.");
        }
        ChartResponse parsed;
        try {
            parsed = mapper.readValue(body, ChartResponse.class);
        } catch (IOException e) {
            throw new StockException("Yahoo Finance returned an unexpected response.");
        }
        // Some 200-class responses still carry a populated error block when
        // the symbol is valid-looking but unsupported. Treat as "not found".
        if (parsed.error().isPresent() && parsed.meta().isEmpty()) {
            throw notFoundException(trimmed, body);
        }
        return parsed.meta().orElseThrow(
                () -> new StockException("Yahoo Finance returned no data for `" + trimmed + "`."));
    }

    private StockException notFoundException(String symbol, byte[] body) {
        // Best-effort: parse the structured error if present, otherwise generic.
        try {
            ChartResponse parsed = mapper.readValue(body, ChartResponse.class);
            String detail = parsed.error()
                    .map(e -> e.description() == null ? "" : e.description())
                    .orElse("");
            if (detail.toLowerCase(Locale.ROOT).contains("delisted")) {
                return new StockException("Couldn't find symbol `" + symbol + "`. "
                        + "It may be delisted, or check the spelling.");
            }
        } catch (IOException ignored) {
            // Fall through to the generic message.
        }
        return new StockException("Couldn't find symbol `" + symbol + "`. "
                + "Try the Yahoo format — e.g. `AAPL` (NASDAQ), `KO` (NYSE), `SHOP.TO` (TSX).");
    }

    /** User-safe checked exception for any fetch/parse failure. */
    static final class StockException extends Exception {
        StockException(String message) { super(message); }
    }
}
