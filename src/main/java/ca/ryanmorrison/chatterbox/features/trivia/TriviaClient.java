package ca.ryanmorrison.chatterbox.features.trivia;

import ca.ryanmorrison.chatterbox.features.trivia.dto.TriviaResponse;
import ca.ryanmorrison.chatterbox.features.trivia.dto.TriviaResultEntry;
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
import java.util.Locale;

/**
 * Talks to <a href="https://opentdb.com">Open Trivia DB</a>'s
 * {@code /api.php} endpoint. No API key.
 *
 * <p>Always requests {@code encode=url3986} so we don't have to deal with
 * the bare HTML-entity flavour of opentdb's default response (which arrives
 * as {@code &quot;}, {@code &#039;}, etc.). URL decoding happens here so
 * callers receive plain text.
 *
 * <p>Open Trivia DB enforces a soft rate limit — 1 request per 5 seconds
 * per IP — and surfaces breaches as {@code response_code: 5}. We translate
 * that into a {@link TriviaException} the handler can show verbatim.
 *
 * <p>Posture mirrors {@code StockClient} / {@code WikiClient}: 10s timeout,
 * 1 MB response cap, all failures funnelled into {@link TriviaException}.
 */
final class TriviaClient {

    static final String BASE_URL = "https://opentdb.com";
    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper mapper;

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
     * Fetch a single random question, optionally constrained by difficulty.
     * {@code difficulty} accepts {@code "easy"}, {@code "medium"},
     * {@code "hard"} or null for any.
     */
    TriviaQuestion fetch(String difficulty) throws TriviaException {
        StringBuilder qs = new StringBuilder("/api.php?amount=1&encode=url3986");
        if (difficulty != null && !difficulty.isBlank()) {
            String d = difficulty.trim().toLowerCase(Locale.ROOT);
            if (!d.equals("easy") && !d.equals("medium") && !d.equals("hard")) {
                throw new TriviaException("Difficulty must be easy, medium, or hard.");
            }
            qs.append("&difficulty=").append(d);
        }
        URI uri;
        try {
            uri = URI.create(baseUrl + qs);
        } catch (IllegalArgumentException e) {
            throw new TriviaException("Failed to build trivia request.");
        }
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
        TriviaResponse parsed;
        try {
            parsed = mapper.readValue(body, TriviaResponse.class);
        } catch (IOException e) {
            throw new TriviaException("Open Trivia DB returned an unexpected response.");
        }
        // Map response_code → exception messages. Code 0 with a populated
        // result is the only happy path; everything else is something the
        // user (or operator) should know about.
        return switch (parsed.responseCode()) {
            case 0 -> firstResult(parsed);
            case 1 -> throw new TriviaException(
                    "No trivia questions matched that filter. Try a different difficulty.");
            case 2 -> throw new TriviaException("Open Trivia DB rejected our request as invalid.");
            case 5 -> throw new TriviaException(
                    "Open Trivia DB is rate-limiting us. Try again in a few seconds.");
            default -> throw new TriviaException(
                    "Open Trivia DB returned response_code " + parsed.responseCode() + ".");
        };
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

    private static String decode(String s) {
        // RFC 3986 percent-decoding; Java's URLDecoder also un-pluses, but
        // url3986-encoded content from opentdb doesn't contain raw '+' in
        // ways that would conflict.
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String decodeOrEmpty(String s) {
        return s == null ? "" : decode(s);
    }

    /** User-safe checked exception for any fetch/parse failure. */
    static final class TriviaException extends Exception {
        TriviaException(String message) { super(message); }
    }
}
