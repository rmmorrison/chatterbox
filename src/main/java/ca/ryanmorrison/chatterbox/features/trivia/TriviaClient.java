package ca.ryanmorrison.chatterbox.features.trivia;

import ca.ryanmorrison.chatterbox.features.trivia.dto.CategoryListResponse;
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

/**
 * Talks to <a href="https://opentdb.com">Open Trivia DB</a>'s
 * {@code /api.php} and {@code /api_category.php} endpoints. No API key.
 *
 * <p>Always requests {@code encode=url3986} for question fetches so we
 * don't have to deal with the bare HTML-entity flavour of opentdb's
 * default response. URL decoding happens here so callers receive plain
 * text.
 *
 * <p>Posture mirrors {@code StockClient} / {@code WikiClient}: 10s timeout,
 * 256 KB response cap, all failures funnelled into {@link TriviaException}.
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
     * Fetch a single random question. Convenience wrapper around
     * {@link #fetchBatch} for callers that want exactly one.
     */
    TriviaQuestion fetch(TriviaFilter filter) throws TriviaException {
        return fetchBatch(filter, 1).get(0);
    }

    /**
     * Fetch {@code amount} random questions in one round-trip. opentdb
     * supports {@code amount} in 1–50; the API guarantees that questions
     * within a single response are distinct, so a session that pre-loads
     * its full round set in one call won't see repeats.
     *
     * <p>If opentdb returns fewer than requested (rare — usually means
     * the filter is unusually narrow), this throws so the caller can
     * abort cleanly rather than silently shrinking the game.
     */
    List<TriviaQuestion> fetchBatch(TriviaFilter filter, int amount) throws TriviaException {
        if (amount < 1 || amount > 50) {
            throw new TriviaException("Trivia batch size must be 1–50.");
        }
        StringBuilder qs = new StringBuilder("/api.php?amount=")
                .append(amount).append("&encode=url3986");
        if (filter != null) {
            if (filter.difficulty() != null) qs.append("&difficulty=").append(filter.difficulty());
            if (filter.categoryId() != null) qs.append("&category=").append(filter.categoryId());
        }
        URI uri;
        try {
            uri = URI.create(baseUrl + qs);
        } catch (IllegalArgumentException e) {
            throw new TriviaException("Failed to build trivia request.");
        }
        TriviaResponse parsed = sendJson(uri, TriviaResponse.class);
        return switch (parsed.responseCode()) {
            case 0 -> parseAllResults(parsed, amount);
            case 1 -> throw new TriviaException(
                    "No more trivia questions matched that filter.");
            case 2 -> throw new TriviaException("Open Trivia DB rejected our request as invalid.");
            case 5 -> throw new TriviaException(
                    "Open Trivia DB is rate-limiting us. Try again in a few seconds.");
            default -> throw new TriviaException(
                    "Open Trivia DB returned response_code " + parsed.responseCode() + ".");
        };
    }

    /**
     * Fetch the full category list from {@code /api_category.php}. Names
     * arrive plain (no url encoding) and are returned in opentdb's order.
     * The list is small and stable enough for a feature module to cache
     * indefinitely; this client doesn't cache anything itself.
     */
    List<CategoryListResponse.Category> fetchCategories() throws TriviaException {
        URI uri;
        try {
            uri = URI.create(baseUrl + "/api_category.php");
        } catch (IllegalArgumentException e) {
            throw new TriviaException("Failed to build category request.");
        }
        CategoryListResponse parsed = sendJson(uri, CategoryListResponse.class);
        if (parsed.triviaCategories() == null || parsed.triviaCategories().isEmpty()) {
            throw new TriviaException("Open Trivia DB returned no categories.");
        }
        return parsed.triviaCategories();
    }

    private <T> T sendJson(URI uri, Class<T> type) throws TriviaException {
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
        if (status == 429) {
            throw new TriviaException(
                    "Open Trivia DB is rate-limiting us. Try again in a few seconds.");
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

    private List<TriviaQuestion> parseAllResults(TriviaResponse parsed, int requested)
            throws TriviaException {
        if (parsed.results() == null || parsed.results().isEmpty()) {
            throw new TriviaException("Open Trivia DB returned no questions.");
        }
        if (parsed.results().size() < requested) {
            throw new TriviaException(
                    "Open Trivia DB only had " + parsed.results().size()
                            + " matching question" + (parsed.results().size() == 1 ? "" : "s")
                            + " (asked for " + requested + "). "
                            + "Try a different filter or fewer rounds.");
        }
        List<TriviaQuestion> out = new ArrayList<>(parsed.results().size());
        for (TriviaResultEntry entry : parsed.results()) {
            out.add(toQuestion(entry));
        }
        return List.copyOf(out);
    }

    private TriviaQuestion toQuestion(TriviaResultEntry entry) throws TriviaException {
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
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String decodeOrEmpty(String s) {
        return s == null ? "" : decode(s);
    }

    /** User-safe checked exception for any fetch/parse failure. */
    static class TriviaException extends Exception {
        TriviaException(String message) { super(message); }
    }
}
