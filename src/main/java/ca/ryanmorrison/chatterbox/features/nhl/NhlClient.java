package ca.ryanmorrison.chatterbox.features.nhl;

import ca.ryanmorrison.chatterbox.features.nhl.dto.Game;
import ca.ryanmorrison.chatterbox.features.nhl.dto.GameDay;
import ca.ryanmorrison.chatterbox.features.nhl.dto.ScheduleResponse;
import ca.ryanmorrison.chatterbox.features.nhl.dto.TeamWeekResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fetches schedule data from {@code api-web.nhle.com}.
 *
 * <p>Mirrors {@code RssFetcher} for posture: 10s timeout, 2 MB body cap,
 * fixed {@code User-Agent}, all failures funnelled into {@link NhlException}
 * with messages that are safe to surface in a Discord reply.
 */
final class NhlClient {

    static final String BASE_URL = "https://api-web.nhle.com";
    static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    static final String USER_AGENT = "Chatterbox/0.1 (+NHL)";

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper mapper;

    NhlClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                BASE_URL);
    }

    /** Test seam — lets tests point at a local {@code HttpServer}. */
    NhlClient(HttpClient http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.mapper = JsonMapper.builder()
                .findAndAddModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /** League-wide schedule for the seven-day window starting today. */
    ScheduleResponse leagueWeek() throws NhlException {
        return get("/v1/schedule/now", ScheduleResponse.class);
    }

    /**
     * Per-team schedule for the seven-day window starting today. The
     * underlying response is a flat games list, which we group by
     * {@code gameDate} so callers can treat both endpoints uniformly.
     *
     * @param teamAbbrev three-letter abbreviation (case-insensitive); the API
     *                   wants upper-case in the path
     */
    ScheduleResponse teamWeek(String teamAbbrev) throws NhlException {
        if (teamAbbrev == null || teamAbbrev.isBlank()) {
            throw new NhlException("A team abbreviation is required.");
        }
        String upper = teamAbbrev.trim().toUpperCase(Locale.ROOT);
        TeamWeekResponse raw = get("/v1/club-schedule/" + upper + "/week/now",
                TeamWeekResponse.class);
        return groupByDate(raw.games());
    }

    /**
     * Buckets a flat games list into {@link GameDay}s in date order, falling
     * back to the start-time-derived UTC date when {@code gameDate} is absent.
     * Games with no usable date sort to the end under a {@code null}-keyed
     * bucket so they're still rendered.
     */
    private static ScheduleResponse groupByDate(List<Game> games) {
        Map<LocalDate, List<Game>> byDate = new LinkedHashMap<>();
        for (Game g : games) {
            LocalDate date = g.gameDate();
            byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(g);
        }
        List<GameDay> days = new ArrayList<>(byDate.size());
        byDate.entrySet().stream()
                .sorted((a, b) -> {
                    if (a.getKey() == null && b.getKey() == null) return 0;
                    if (a.getKey() == null) return 1;
                    if (b.getKey() == null) return -1;
                    return a.getKey().compareTo(b.getKey());
                })
                .forEach(e -> days.add(new GameDay(e.getKey(), e.getValue())));
        return new ScheduleResponse(days);
    }

    private <T> T get(String path, Class<T> type) throws NhlException {
        URI uri;
        try {
            uri = URI.create(baseUrl + path);
        } catch (IllegalArgumentException e) {
            throw new NhlException("Couldn't build the request URL.");
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
        } catch (IOException e) {
            throw new NhlException("Couldn't reach the NHL API.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NhlException("Request was interrupted.");
        }
        int status = resp.statusCode();
        if (status == 404) {
            // Surfaces as the team-not-found path; the league endpoint should never 404.
            throw new NhlException("The NHL API didn't recognise that team.");
        }
        if (status / 100 != 2) {
            throw new NhlException("The NHL API returned HTTP " + status + ".");
        }
        byte[] body = resp.body();
        if (body == null || body.length == 0) {
            throw new NhlException("The NHL API returned an empty response.");
        }
        if (body.length > MAX_RESPONSE_BYTES) {
            throw new NhlException("The NHL API response was too large.");
        }
        try {
            return mapper.readValue(body, type);
        } catch (IOException e) {
            throw new NhlException("The NHL API returned an unexpected response.");
        }
    }

    /** User-safe checked exception for any fetch/parse failure. */
    static final class NhlException extends Exception {
        NhlException(String message) { super(message); }
    }
}
