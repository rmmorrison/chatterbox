package ca.ryanmorrison.chatterbox.features.trivia;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriviaClientTest {

    private HttpServer server;
    private TriviaClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        client = new TriviaClient(http, baseUrl);
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

    private static final String SAMPLE_MULTIPLE = """
            {
              "response_code": 0,
              "results": [{
                "type": "multiple",
                "difficulty": "easy",
                "category": "Entertainment%3A%20Video%20Games",
                "question": "What%20is%20the%20name%20of%20the%20protagonist%20in%20the%20Sonic%20series%3F",
                "correct_answer": "Sonic",
                "incorrect_answers": ["Tails", "Knuckles", "Dr.%20Eggman"]
              }]
            }
            """;

    private static final String SAMPLE_BOOLEAN = """
            {
              "response_code": 0,
              "results": [{
                "type": "boolean",
                "difficulty": "medium",
                "category": "Science%3A%20Computers",
                "question": "Java%20is%20a%20pure%20object-oriented%20language.",
                "correct_answer": "False",
                "incorrect_answers": ["True"]
              }]
            }
            """;

    @Test
    void parsesMultipleChoiceAndDecodesUrlEscapes() throws Exception {
        serve("/api.php", 200, SAMPLE_MULTIPLE);
        TriviaQuestion q = client.fetch(TriviaFilter.any());
        assertEquals(TriviaQuestion.Type.MULTIPLE, q.type());
        assertEquals("easy", q.difficulty());
        assertEquals("Entertainment: Video Games", q.category());
        assertEquals("What is the name of the protagonist in the Sonic series?", q.question());
        assertEquals("Sonic", q.correctAnswer());
        assertTrue(q.incorrectAnswers().contains("Dr. Eggman"));
    }

    @Test
    void parsesBooleanType() throws Exception {
        serve("/api.php", 200, SAMPLE_BOOLEAN);
        TriviaQuestion q = client.fetch(TriviaFilter.any());
        assertEquals(TriviaQuestion.Type.BOOLEAN, q.type());
        assertEquals("False", q.correctAnswer());
    }

    @Test
    void filterParamsForwardedAsQueryString() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server.createContext("/api.php", ex -> {
            rawQuery.set(ex.getRequestURI().getRawQuery());
            byte[] bytes = SAMPLE_MULTIPLE.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        client.fetch(new TriviaFilter(15, "hard"), "tok-abc");
        assertTrue(rawQuery.get().contains("difficulty=hard"), () -> "got: " + rawQuery.get());
        assertTrue(rawQuery.get().contains("category=15"), () -> "got: " + rawQuery.get());
        assertTrue(rawQuery.get().contains("token=tok-abc"), () -> "got: " + rawQuery.get());
        assertTrue(rawQuery.get().contains("encode=url3986"));
    }

    @Test
    void noFilterMeansNoConstraintParams() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server.createContext("/api.php", ex -> {
            rawQuery.set(ex.getRequestURI().getRawQuery());
            byte[] bytes = SAMPLE_MULTIPLE.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        client.fetch(TriviaFilter.any());
        assertTrue(!rawQuery.get().contains("difficulty="),
                "no difficulty filter should not appear in the query");
        assertTrue(!rawQuery.get().contains("category="),
                "no category filter should not appear in the query");
        assertTrue(!rawQuery.get().contains("token="));
    }

    @Test
    void rejectsUnknownDifficulty() {
        assertThrows(TriviaClient.TriviaException.class,
                () -> TriviaFilter.validated(null, "legendary"));
    }

    @Test
    void rejectsUnknownCategory() {
        assertThrows(TriviaClient.TriviaException.class,
                () -> TriviaFilter.validated(99999, null));
    }

    @Test
    void rateLimitedResponseSurfacesFriendlyMessage() {
        serve("/api.php", 200, """
                {"response_code": 5, "results": []}
                """);
        var ex = assertThrows(TriviaClient.TriviaException.class,
                () -> client.fetch(TriviaFilter.any()));
        assertTrue(ex.getMessage().toLowerCase().contains("rate"), () -> ex.getMessage());
    }

    @Test
    void noResultsResponseSurfacesAdvice() {
        serve("/api.php", 200, """
                {"response_code": 1, "results": []}
                """);
        var ex = assertThrows(TriviaClient.TriviaException.class,
                () -> client.fetch(TriviaFilter.any()));
        assertTrue(ex.getMessage().toLowerCase().contains("no more"), () -> ex.getMessage());
    }

    @Test
    void invalidParameterResponseIsCalledOut() {
        serve("/api.php", 200, """
                {"response_code": 2, "results": []}
                """);
        var ex = assertThrows(TriviaClient.TriviaException.class,
                () -> client.fetch(TriviaFilter.any()));
        assertTrue(ex.getMessage().toLowerCase().contains("invalid"), () -> ex.getMessage());
    }

    @Test
    void tokenExhaustionIsTypedSubclassForRetry() {
        // response_code 4 = token empty (no more unique questions for this token).
        // The handler retries tokenless on this; the typed exception lets it.
        serve("/api.php", 200, """
                {"response_code": 4, "results": []}
                """);
        assertThrows(TriviaClient.TokenExhaustedException.class,
                () -> client.fetch(TriviaFilter.any(), "exhausted"));
    }

    @Test
    void httpFiveHundredSurfacesAsHttpError() {
        serve("/api.php", 500, "internal error");
        var ex = assertThrows(TriviaClient.TriviaException.class,
                () -> client.fetch(TriviaFilter.any()));
        assertTrue(ex.getMessage().contains("500"), () -> ex.getMessage());
    }

    @Test
    void unparseableBodyIsCalledOut() {
        serve("/api.php", 200, "{not json");
        var ex = assertThrows(TriviaClient.TriviaException.class,
                () -> client.fetch(TriviaFilter.any()));
        assertTrue(ex.getMessage().toLowerCase().contains("unexpected"), () -> ex.getMessage());
    }

    // -- session token endpoint ---------------------------------------------

    @Test
    void requestSessionTokenReturnsTokenOnSuccess() throws Exception {
        serve("/api_token.php", 200, """
                {"response_code": 0, "response_message": "Token Generated Successfully!", "token": "abc123"}
                """);
        Optional<String> token = client.requestSessionToken();
        assertEquals("abc123", token.orElseThrow());
    }

    @Test
    void requestSessionTokenEmptyOnError() throws Exception {
        serve("/api_token.php", 200, """
                {"response_code": 1, "response_message": "fail", "token": null}
                """);
        assertTrue(client.requestSessionToken().isEmpty());
    }
}
