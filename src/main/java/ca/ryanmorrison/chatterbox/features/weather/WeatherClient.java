package ca.ryanmorrison.chatterbox.features.weather;

import ca.ryanmorrison.chatterbox.features.weather.dto.WeatherResponse;
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
 * Talks to {@code wttr.in}.
 *
 * <p>Posture mirrors {@code NhlClient} / {@code FrinkiacClient}: 10s
 * timeout, response-size cap, all failures funnelled into
 * {@link WeatherException} with messages safe to surface in a Discord
 * reply. wttr.in defaults its response format to ANSI text or HTML
 * depending on UA, so we always pin {@code ?format=j1} to get JSON.
 *
 * <p>Failure mapping follows the live-probed behaviour:
 * <ul>
 *   <li>Unknown location → upstream replies HTTP 500 with the plaintext
 *       body {@code "location not found: …"}. We sniff that body shape
 *       and surface a "location not found" message rather than a generic
 *       "HTTP 500" — that's the difference between a retryable hint and
 *       a typo correction.</li>
 *   <li>HTTP 429 → friendly "rate limited" message (wttr.in caps free
 *       usage at ~30 req/hour per IP).</li>
 *   <li>Other non-2xx → generic "HTTP N" error.</li>
 * </ul>
 */
final class WeatherClient {

    static final String BASE_URL = "https://wttr.in";
    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    static final String USER_AGENT = "Chatterbox/0.1 (+/weather; via wttr.in)";

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper mapper;

    WeatherClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                BASE_URL);
    }

    /** Test seam — lets tests point at a local {@code HttpServer}. */
    WeatherClient(HttpClient http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.mapper = JsonMapper.builder()
                .findAndAddModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /**
     * Fetch parsed weather for {@code location}. Empty / blank input is
     * rejected client-side because wttr.in interprets it as "use my IP",
     * which from the bot's perspective would silently return the
     * datacentre's location instead of the user's intent.
     */
    WeatherResponse fetch(String location) throws WeatherException {
        if (location == null || location.isBlank()) {
            throw new WeatherException("Tell me a location.");
        }
        // URLEncoder uses form-encoding (+ for space); paths need %20.
        String encoded = URLEncoder.encode(location.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        URI uri;
        try {
            uri = URI.create(baseUrl + "/" + encoded + "?format=j1");
        } catch (IllegalArgumentException e) {
            throw new WeatherException("That doesn't look like a valid location.");
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
            throw new WeatherException("wttr.in didn't respond within "
                    + HTTP_TIMEOUT.toSeconds() + " seconds.");
        } catch (IOException e) {
            throw new WeatherException("Couldn't reach wttr.in.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WeatherException("Request was interrupted.");
        }
        int status = resp.statusCode();
        byte[] body = resp.body() == null ? new byte[0] : resp.body();
        if (body.length > MAX_RESPONSE_BYTES) {
            throw new WeatherException("wttr.in response was too large.");
        }

        if (status == 429) {
            throw new WeatherException("wttr.in is rate-limiting us. Try again in a minute.");
        }
        if (status / 100 != 2) {
            // Sniff the unknown-location body before falling back to a generic
            // "HTTP N" — the upstream returns 500 (not 404) for typos.
            String text = new String(body, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (text.contains("location not found") || text.contains("unknown location")) {
                throw new WeatherException("Couldn't find a place called `" + location.trim() + "`. "
                        + "Try a city name, country, postal code, or `lat,long`.");
            }
            throw new WeatherException("wttr.in returned HTTP " + status + ".");
        }
        if (body.length == 0) {
            throw new WeatherException("wttr.in returned an empty response.");
        }
        try {
            return mapper.readValue(body, WeatherResponse.class);
        } catch (IOException e) {
            throw new WeatherException("wttr.in returned an unexpected response.");
        }
    }

    /** User-safe checked exception for any fetch/parse failure. */
    static final class WeatherException extends Exception {
        WeatherException(String message) { super(message); }
    }
}
