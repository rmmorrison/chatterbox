package ca.ryanmorrison.chatterbox.features.nhl;

import ca.ryanmorrison.chatterbox.features.nhl.dto.Game;
import ca.ryanmorrison.chatterbox.features.nhl.dto.GameDay;
import ca.ryanmorrison.chatterbox.features.nhl.dto.ScheduleResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NhlClientTest {

    private HttpServer server;
    private NhlClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        client = new NhlClient(http, baseUrl);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private void serve(String path, int status, String body) {
        server.createContext(path, ex -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
    }

    private static final String SAMPLE = """
            {
              "gameWeek": [
                {
                  "date": "2026-05-02",
                  "games": [
                    {
                      "id": 2025030171,
                      "startTimeUTC": "2026-05-02T23:00:00Z",
                      "gameState": "FUT",
                      "awayTeam": { "abbrev": "EDM", "score": 0 },
                      "homeTeam": { "abbrev": "TOR", "score": 0 }
                    },
                    {
                      "id": 2025030172,
                      "startTimeUTC": "2026-05-03T01:00:00Z",
                      "gameState": "LIVE",
                      "awayTeam": { "abbrev": "BOS", "score": 1 },
                      "homeTeam": { "abbrev": "MTL", "score": 2 }
                    }
                  ]
                },
                {
                  "date": "2026-05-03",
                  "games": []
                }
              ]
            }
            """;

    @Test
    void leagueWeekParsesGamesAndDates() throws Exception {
        serve("/v1/schedule/now", 200, SAMPLE);
        ScheduleResponse resp = client.leagueWeek();

        assertEquals(2, resp.gameWeek().size());
        GameDay day1 = resp.gameWeek().get(0);
        assertEquals(LocalDate.of(2026, 5, 2), day1.date());
        assertEquals(2, day1.games().size());

        Game first = day1.games().get(0);
        assertEquals(Instant.parse("2026-05-02T23:00:00Z"), first.startTimeUtc());
        assertEquals("FUT", first.gameState());
        assertEquals("EDM", first.awayTeam().abbrev());
        assertEquals("TOR", first.homeTeam().abbrev());
    }

    private static final String TEAM_SAMPLE = """
            {
              "previousStartDate": "2026-04-25",
              "clubTimezone": "US/Eastern",
              "games": [
                {
                  "id": 2025030221,
                  "gameDate": "2026-05-02",
                  "startTimeUTC": "2026-05-03T00:00:00Z",
                  "gameState": "FUT",
                  "awayTeam": { "abbrev": "PHI", "score": 0 },
                  "homeTeam": { "abbrev": "CAR", "score": 0 }
                },
                {
                  "id": 2025030222,
                  "gameDate": "2026-05-04",
                  "startTimeUTC": "2026-05-04T23:00:00Z",
                  "gameState": "FUT",
                  "awayTeam": { "abbrev": "PHI", "score": 0 },
                  "homeTeam": { "abbrev": "CAR", "score": 0 }
                }
              ]
            }
            """;

    @Test
    void teamWeekUppercasesAbbreviationAndGroupsGamesByDate() throws Exception {
        serve("/v1/club-schedule/PHI/week/now", 200, TEAM_SAMPLE);
        ScheduleResponse resp = client.teamWeek("phi");

        // Two games on different dates → two GameDay buckets in chronological order.
        assertEquals(2, resp.gameWeek().size());
        GameDay day1 = resp.gameWeek().get(0);
        assertEquals(LocalDate.of(2026, 5, 2), day1.date());
        assertEquals(1, day1.games().size());
        assertEquals("PHI", day1.games().get(0).awayTeam().abbrev());
        assertEquals("CAR", day1.games().get(0).homeTeam().abbrev());

        GameDay day2 = resp.gameWeek().get(1);
        assertEquals(LocalDate.of(2026, 5, 4), day2.date());
        assertEquals(1, day2.games().size());
    }

    @Test
    void teamWeekGroupsMultipleGamesOnSameDate() throws Exception {
        String body = """
                {
                  "games": [
                    { "id": 1, "gameDate": "2026-05-02", "startTimeUTC": "2026-05-02T22:00:00Z",
                      "gameState": "FUT", "awayTeam": { "abbrev": "PHI" }, "homeTeam": { "abbrev": "CAR" } },
                    { "id": 2, "gameDate": "2026-05-02", "startTimeUTC": "2026-05-03T00:00:00Z",
                      "gameState": "FUT", "awayTeam": { "abbrev": "PHI" }, "homeTeam": { "abbrev": "CAR" } }
                  ]
                }
                """;
        serve("/v1/club-schedule/PHI/week/now", 200, body);
        ScheduleResponse resp = client.teamWeek("PHI");
        assertEquals(1, resp.gameWeek().size());
        assertEquals(2, resp.gameWeek().get(0).games().size());
    }

    @Test
    void notFoundIsReportedAsUnknownTeam() {
        serve("/v1/club-schedule/ZZZ/week/now", 404, "{\"detail\":\"not found\"}");
        var ex = assertThrows(NhlClient.NhlException.class, () -> client.teamWeek("ZZZ"));
        assertTrue(ex.getMessage().toLowerCase().contains("team"), () -> "got: " + ex.getMessage());
    }

    @Test
    void serverErrorBubblesThroughWithStatusInMessage() {
        serve("/v1/schedule/now", 503, "down");
        var ex = assertThrows(NhlClient.NhlException.class, () -> client.leagueWeek());
        assertTrue(ex.getMessage().contains("503"), () -> "got: " + ex.getMessage());
    }

    @Test
    void unparseableBodyIsReportedAsUnexpected() {
        serve("/v1/schedule/now", 200, "{not json");
        var ex = assertThrows(NhlClient.NhlException.class, () -> client.leagueWeek());
        assertTrue(ex.getMessage().toLowerCase().contains("unexpected"),
                () -> "got: " + ex.getMessage());
    }

    @Test
    void emptyAbbreviationRejected() {
        var ex = assertThrows(NhlClient.NhlException.class, () -> client.teamWeek(" "));
        assertTrue(ex.getMessage().toLowerCase().contains("team"), () -> "got: " + ex.getMessage());
    }
}
