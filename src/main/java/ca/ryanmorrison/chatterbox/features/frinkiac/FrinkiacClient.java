package ca.ryanmorrison.chatterbox.features.frinkiac;

import ca.ryanmorrison.chatterbox.features.frinkiac.dto.SearchResult;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Talks to {@code frinkiac.com}.
 *
 * <p>Endpoints used:
 * <ul>
 *   <li>{@code GET /api/search?q=...} → list of {@link SearchResult}</li>
 *   <li>{@code GET /img/{episode}/{timestamp}/medium.jpg} → uncaptioned frame bytes</li>
 *   <li>{@code GET /comic/img?b64=...} → captioned frame bytes (single-panel comic)</li>
 * </ul>
 *
 * <p>Browsing search results uses the plain {@code /img/...} endpoint so we
 * only invoke the heavier comic renderer once the user has actually entered
 * caption text. The image endpoints sit behind a Cloudflare bot rule that
 * returns 403 for requests without a browser-like {@code User-Agent} and
 * {@code Referer}, so those headers are sent on every call.
 *
 * <p>Posture mirrors {@code NhlClient}: 10s timeout, response-size cap, all
 * failures funnelled into {@link FrinkiacException} with messages safe to
 * surface in a Discord reply.
 */
final class FrinkiacClient {

    static final String BASE_URL = "https://frinkiac.com";
    static final int MAX_JSON_BYTES = 1 * 1024 * 1024;
    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    /** Cloudflare lets browser-shaped requests through; bare UAs get 403. */
    static final String USER_AGENT =
            "Mozilla/5.0 (compatible; Chatterbox/0.1; +Frinkiac integration)";
    static final String REFERER = "https://frinkiac.com/";

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper mapper;

    FrinkiacClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                BASE_URL);
    }

    /** Test seam — lets tests point at a local {@code HttpServer}. */
    FrinkiacClient(HttpClient http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.mapper = JsonMapper.builder()
                .findAndAddModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // Jackson 3 enables FAIL_ON_NULL_FOR_PRIMITIVES by default; these
                // upstream APIs omit optional primitive fields, which the DTOs
                // expect to default to zero as in Jackson 2.
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                .build();
    }

    /** Search for frames matching {@code query}. Empty / whitespace queries are rejected up-front. */
    List<SearchResult> search(String query) throws FrinkiacException {
        if (query == null || query.isBlank()) {
            throw new FrinkiacException("A search query is required.");
        }
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        byte[] body = getBytes("/api/search?q=" + encoded, MAX_JSON_BYTES);
        try {
            return mapper.readValue(body, new TypeReference<List<SearchResult>>() {});
        } catch (JacksonException e) {
            throw new FrinkiacException("Frinkiac returned an unexpected search response.");
        }
    }

    /** Raw, uncaptioned medium-sized frame image. */
    byte[] fetchFrame(String episode, long timestamp) throws FrinkiacException {
        if (episode == null || episode.isBlank()) {
            throw new FrinkiacException("Episode key is required.");
        }
        String path = "/img/" + episode.trim().toUpperCase(Locale.ROOT)
                + "/" + timestamp + "/medium.jpg";
        return getBytes(path, MAX_IMAGE_BYTES);
    }

    /**
     * Captioned single-frame image, rendered server-side. {@code text} may
     * contain newlines for multi-line captions — Frinkiac respects them.
     */
    byte[] fetchCaptionedFrame(String episode, long timestamp, String text) throws FrinkiacException {
        if (episode == null || episode.isBlank()) {
            throw new FrinkiacException("Episode key is required.");
        }
        String json;
        try {
            json = mapper.writeValueAsString(
                    CaptionOverlay.singlePanel(episode.trim().toUpperCase(Locale.ROOT), timestamp,
                            text == null ? "" : text));
        } catch (JacksonException e) {
            throw new FrinkiacException("Couldn't serialise the caption payload.");
        }
        // URL-safe alphabet (- and _ instead of + and /) — the comic renderer's b64
        // decoder rejects standard base64 with "error decoding comic b64" when the
        // payload's byte alignment happens to produce a + or /.
        String b64 = Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String encoded = URLEncoder.encode(b64, StandardCharsets.UTF_8);
        return getBytes("/comic/img?b64=" + encoded, MAX_IMAGE_BYTES);
    }

    private byte[] getBytes(String path, int maxBytes) throws FrinkiacException {
        URI uri;
        try {
            uri = URI.create(baseUrl + path);
        } catch (IllegalArgumentException e) {
            throw new FrinkiacException("Couldn't build the request URL.");
        }
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Accept", "application/json,image/*;q=0.9,*/*;q=0.5")
                .GET()
                .build();
        HttpResponse<byte[]> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new FrinkiacException("Couldn't reach Frinkiac.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FrinkiacException("Request was interrupted.");
        }
        int status = resp.statusCode();
        if (status / 100 != 2) {
            throw new FrinkiacException("Frinkiac returned HTTP " + status + ".");
        }
        byte[] body = resp.body();
        if (body == null || body.length == 0) {
            throw new FrinkiacException("Frinkiac returned an empty response.");
        }
        if (body.length > maxBytes) {
            throw new FrinkiacException("Frinkiac response was too large.");
        }
        return body;
    }

    /** User-safe checked exception for any fetch/parse failure. */
    static final class FrinkiacException extends Exception {
        FrinkiacException(String message) { super(message); }
    }
}
