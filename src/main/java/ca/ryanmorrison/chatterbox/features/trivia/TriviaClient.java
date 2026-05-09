package ca.ryanmorrison.chatterbox.features.trivia;

import ca.ryanmorrison.chatterbox.features.trivia.dto.TriviaResponse;
import ca.ryanmorrison.chatterbox.features.trivia.dto.TriviaResultEntry;
import ca.ryanmorrison.chatterbox.features.trivia.dto.TriviaTokenResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Talks to <a href="https://opentdb.com">Open Trivia DB</a>'s
 * {@code /api.php} endpoint. No API key.
 *
 * <p>Always requests {@code encode=url3986} so we don't have to deal with
 * the bare HTML-entity flavour of opentdb's default response. URL decoding
 * happens here so callers receive plain text.
 *
 * <p>Open Trivia DB enforces a soft rate limit — 1 request per 5 seconds
 * per IP — and surfaces breaches as {@code response_code: 5}.
 *
 * <p>Session tokens (see {@link #requestSessionToken}) are optional but
 * recommended: a fetch call passing a token won't repeat any question
 * served against that token until it's reset (response_code 4) or expired
 * server-side (response_code 3) — useful so a single multi-round game
 * doesn't repeat itself.
 *
 * <p>Posture mirrors {@code StockClient} / {@code WikiClient}: 10s timeout,
 * 256 KB response cap, all failures funnelled into {@link TriviaException}.
 */
final class TriviaClient {

    static final String BASE_URL = "https://opentdb.com";
    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    /**
     * Open Trivia DB caps each IP at one request per ~5 seconds; busting that
     * yields 429s with no Retry-After. We pad to 5.5s for safety and gate
     * every outbound call (token + question fetches alike) so the client
     * self-paces regardless of how fast the game flows.
     */
    static final long MIN_FETCH_INTERVAL_MS = 5_500L;

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper mapper;
    private final Object fetchGate = new Object();
    /** Earliest epoch-ms a future fetch is allowed to leave the gate. */
    private long nextFetchAllowedAt = 0L;

    TriviaClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                BASE_URL);
    }

    /** Test seam — point at a local mock server. */
    TriviaClient(HttpClient http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.mapper = JsonMapper.builder()
                .findAndAddModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /**
     * Fetch a single random question matching {@code filter}. If
     * {@code sessionToken} is non-null, opentdb won't repeat questions
     * already served against it within this session.
     */
    TriviaQuestion fetch(TriviaFilter filter, String sessionToken) throws TriviaException {
        StringBuilder qs = new StringBuilder("/api.php?amount=1&encode=url3986");
        if (filter != null) {
            if (filter.difficulty() != null) qs.append("&difficulty=").append(filter.difficulty());
            if (filter.categoryId() != null) qs.append("&category=").append(filter.categoryId());
        }
        if (sessionToken != null && !sessionToken.isBlank()) {
            qs.append("&token=").append(sessionToken);
        }
        URI uri;
        try {
            uri = URI.create(baseUrl + qs);
        } catch (IllegalArgumentException e) {
            throw new TriviaException("Failed to build trivia request.");
        }
        TriviaResponse parsed = sendJson(uri, TriviaResponse.class);
        return switch (parsed.responseCode()) {
            case 0 -> firstResult(parsed);
            case 1 -> throw new TriviaException(
                    "No more trivia questions matched that filter.");
            case 2 -> throw new TriviaException("Open Trivia DB rejected our request as invalid.");
            case 3, 4 -> throw new TokenExhaustedException();
            case 5 -> throw new TriviaException(
                    "Open Trivia DB is rate-limiting us. Try again in a few seconds.");
            default -> throw new TriviaException(
                    "Open Trivia DB returned response_code " + parsed.responseCode() + ".");
        };
    }

    /** Convenience overload: same as {@link #fetch(TriviaFilter, String)} with no session token. */
    TriviaQuestion fetch(TriviaFilter filter) throws TriviaException {
        return fetch(filter, null);
    }

    /**
     * Request a fresh session token. Pass it back to {@link #fetch} so the
     * same question isn't returned twice within one game. Tokens last 6
     * hours server-side.
     */
    Optional<String> requestSessionToken() throws TriviaException {
        URI uri;
        try {
            uri = URI.create(baseUrl + "/api_token.php?command=request");
        } catch (IllegalArgumentException e) {
            throw new TriviaException("Failed to build token request.");
        }
        TriviaTokenResponse parsed = sendJson(uri, TriviaTokenResponse.class);
        if (parsed.responseCode() != 0 || parsed.token() == null || parsed.token().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(parsed.token());
    }

    private <T> T sendJson(URI uri, Class<T> type) throws TriviaException {
        waitForRateLimitGate();
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<byte[]> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw new TriviaException("Open Trivia DB didn't respond within "
                    + HTTP_TIMEOUT.toSeconds() + " seconds.");
        } catch (IOException e) {
            throw new TriviaException("Couldn't reach Open Trivia DB.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TriviaException("Request was interrupted.");
        }
        int status = resp.statusCode();
        byte[] body = resp.body() == null ? new byte[0] : resp.body();
        if (body.length > MAX_RESPONSE_BYTES) {
            throw new TriviaException("Open Trivia DB response was too large.");
        }
        if (status / 100 != 2) {
            throw new TriviaException("Open Trivia DB returned HTTP " + status + ".");
        }
        if (body.length == 0) {
            throw new TriviaException("Open Trivia DB returned an empty response.");
        }
        try {
            return mapper.readValue(body, type);
        } catch (IOException e) {
            throw new TriviaException("Open Trivia DB returned an unexpected response.");
        }
    }

    private TriviaQuestion firstResult(TriviaResponse parsed) throws TriviaException {
        if (parsed.results() == null || parsed.results().isEmpty()) {
            throw new TriviaException("Open Trivia DB returned no questions.");
        }
        TriviaResultEntry entry = parsed.results().get(0);
        if (entry == null || entry.question() == null || entry.correctAnswer() == null) {
            throw new TriviaException("Open Trivia DB returned a malformed question.");
        }
        TriviaQuestion.Type type = "boolean".equalsIgnoreCase(entry.type())
                ? TriviaQuestion.Type.BOOLEAN
                : TriviaQuestion.Type.MULTIPLE;

        List<String> wrong = new ArrayList<>();
        if (entry.incorrectAnswers() != null) {
            for (String w : entry.incorrectAnswers()) {
                if (w != null) wrong.add(decode(w));
            }
        }
        return new TriviaQuestion(
                type,
                decodeOrEmpty(entry.difficulty()),
                decodeOrEmpty(entry.category()),
                decode(entry.question()),
                decode(entry.correctAnswer()),
                List.copyOf(wrong));
    }

    /**
     * Pace outbound calls so any two are at least {@link #MIN_FETCH_INTERVAL_MS}
     * apart. Reserves the next slot inside the lock then sleeps outside it so
     * concurrent callers stack rather than block each other on the monitor.
     */
    private void waitForRateLimitGate() throws TriviaException {
        long waitMs;
        synchronized (fetchGate) {
            long now = System.currentTimeMillis();
            long start = Math.max(now, nextFetchAllowedAt);
            waitMs = start - now;
            nextFetchAllowedAt = start + MIN_FETCH_INTERVAL_MS;
        }
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TriviaException("Request was interrupted.");
            }
        }
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String decodeOrEmpty(String s) {
        return s == null ? "" : decode(s);
    }

    /** User-safe checked exception for any fetch/parse failure. */
    static class TriviaException extends Exception {
        TriviaException(String message) { super(message); }
    }

    /**
     * Signals that the session token has been exhausted (response_code 4)
     * or expired (response_code 3). The handler should request a fresh
     * token and retry — or fall back to an untoked request.
     */
    static final class TokenExhaustedException extends TriviaException {
        TokenExhaustedException() { super("Open Trivia DB session token expired."); }
    }
}
