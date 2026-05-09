package ca.ryanmorrison.chatterbox.features.wiki;

import ca.ryanmorrison.chatterbox.features.wiki.dto.PageSummary;
import ca.ryanmorrison.chatterbox.features.wiki.dto.SearchHit;
import ca.ryanmorrison.chatterbox.features.wiki.dto.SearchResponse;
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
import java.util.List;
import java.util.Optional;

/**
 * Talks to Wikipedia's REST API.
 *
 * <p>Two endpoints used:
 * <ul>
 *   <li>{@code GET /api/rest_v1/page/summary/{title}} — page summary (lead
 *       paragraph + description + thumbnail).</li>
 *   <li>{@code GET /w/rest.php/v1/search/page?q=...&limit=N} — fallback
 *       full-text search when the direct title lookup 404s.</li>
 * </ul>
 *
 * <p>Both follow the project's standard posture: 10s timeout, response-size
 * cap, all failures funnelled into {@link WikiException} with messages safe
 * to surface in a Discord reply.
 */
final class WikiClient {

    static final String BASE_URL = "https://en.wikipedia.org";
    static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    static final String USER_AGENT = "Chatterbox/0.1 (+/wiki Discord bot)";

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper mapper;

    WikiClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                BASE_URL);
    }

    /** Test seam — lets tests point at a local {@code HttpServer}. */
    WikiClient(HttpClient http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.mapper = JsonMapper.builder()
                .findAndAddModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /**
     * Direct page summary lookup. Returns {@link Optional#empty()} on 404
     * (so callers can chain into {@link #search}); other non-2xx surface
     * as {@link WikiException}.
     */
    Optional<PageSummary> summary(String title) throws WikiException {
        if (title == null || title.isBlank()) {
            throw new WikiException("Tell me what to look up.");
        }
        // URLEncoder uses form-encoding (+ for space); paths need %20.
        String encoded = URLEncoder.encode(title.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        byte[] body;
        int status;
        try {
            HttpResponse<byte[]> resp = send("/api/rest_v1/page/summary/" + encoded);
            body = resp.body() == null ? new byte[0] : resp.body();
            status = resp.statusCode();
        } catch (WikiException e) {
            throw e;
        }

        if (status == 404) return Optional.empty();
        if (status == 429) throw new WikiException("Wikipedia is rate-limiting us. Try again in a minute.");
        if (status / 100 != 2) throw new WikiException("Wikipedia returned HTTP " + status + ".");
        if (body.length == 0) throw new WikiException("Wikipedia returned an empty response.");
        try {
            return Optional.of(mapper.readValue(body, PageSummary.class));
        } catch (IOException e) {
            throw new WikiException("Wikipedia returned an unexpected response.");
        }
    }

    /**
     * Full-text search. Used as a fallback when {@link #summary} 404s, so we
     * can convert "torono" → "Toronto". Returns at most {@code limit} hits.
     */
    List<SearchHit> search(String query, int limit) throws WikiException {
        if (query == null || query.isBlank()) return List.of();
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        byte[] body;
        int status;
        try {
            HttpResponse<byte[]> resp =
                    send("/w/rest.php/v1/search/page?q=" + encoded + "&limit=" + limit);
            body = resp.body() == null ? new byte[0] : resp.body();
            status = resp.statusCode();
        } catch (WikiException e) {
            throw e;
        }

        if (status == 429) throw new WikiException("Wikipedia is rate-limiting us. Try again in a minute.");
        if (status / 100 != 2) throw new WikiException("Wikipedia returned HTTP " + status + ".");
        if (body.length == 0) return List.of();
        try {
            return mapper.readValue(body, SearchResponse.class).hits();
        } catch (IOException e) {
            throw new WikiException("Wikipedia returned an unexpected response.");
        }
    }

    private HttpResponse<byte[]> send(String path) throws WikiException {
        URI uri;
        try {
            uri = URI.create(baseUrl + path);
        } catch (IllegalArgumentException e) {
            throw new WikiException("Couldn't build the request URL.");
        }
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = resp.body();
            if (body != null && body.length > MAX_RESPONSE_BYTES) {
                throw new WikiException("Wikipedia response was too large.");
            }
            return resp;
        } catch (HttpTimeoutException e) {
            throw new WikiException("Wikipedia didn't respond within " + HTTP_TIMEOUT.toSeconds() + " seconds.");
        } catch (IOException e) {
            throw new WikiException("Couldn't reach Wikipedia.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WikiException("Request was interrupted.");
        }
    }

    /** User-safe checked exception for any fetch/parse failure. */
    static final class WikiException extends Exception {
        WikiException(String message) { super(message); }
    }
}
