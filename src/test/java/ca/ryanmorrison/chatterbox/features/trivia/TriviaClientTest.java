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
        client.fetch(new TriviaFilter(15, "hard"));
        assertTrue(rawQuery.get().contains("difficulty=hard"), () -> "got: " + rawQuery.get());
        assertTrue(rawQuery.get().contains("category=15"), () -> "got: " + rawQuery.get());
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
    }

    @Test
    void rejectsUnknownDifficulty() {
        assertThrows(TriviaClient.TriviaException.class,
                () -> TriviaFilter.validated(null, "legendary"));
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

    // -- categories endpoint -----------------------------------------------

    @Test
    void fetchCategoriesParsesIdAndNameInOrder() throws Exception {
        serve("/api_category.php", 200, """
                {"trivia_categories": [
                    {"id": 9, "name": "General Knowledge"},
                    {"id": 22, "name": "Geography"},
                    {"id": 18, "name": "Science: Computers"}
                ]}
                """);
        var got = client.fetchCategories();
        assertEquals(3, got.size());
        assertEquals(9, got.get(0).id());
        assertEquals("General Knowledge", got.get(0).name());
        assertEquals(22, got.get(1).id());
        assertEquals("Science: Computers", got.get(2).name());
    }

    @Test
    void fetchCategoriesEmptyResponseThrows() {
        serve("/api_category.php", 200, """
                {"trivia_categories": []}
                """);
        assertThrows(TriviaClient.TriviaException.class, () -> client.fetchCategories());
    }

    @Test
    void fetchCategoriesHttpErrorSurfaces() {
        serve("/api_category.php", 503, "down");
        var ex = assertThrows(TriviaClient.TriviaException.class, () -> client.fetchCategories());
        assertTrue(ex.getMessage().contains("503"), () -> ex.getMessage());
    }

    // -- fetchBatch (pre-loaded games) -------------------------------------

    private static final String SAMPLE_BATCH_OF_THREE = """
            {
              "response_code": 0,
              "results": [
                {"type":"multiple","difficulty":"easy","category":"A%26B",
                 "question":"Q1%3F","correct_answer":"X1","incorrect_answers":["W1","W2","W3"]},
                {"type":"boolean","difficulty":"easy","category":"A%26B",
                 "question":"Q2%3F","correct_answer":"True","incorrect_answers":["False"]},
                {"type":"multiple","difficulty":"easy","category":"A%26B",
                 "question":"Q3%3F","correct_answer":"X3","incorrect_answers":["W1","W2","W3"]}
              ]
            }
            """;

    @Test
    void fetchBatchSendsAmountAndReturnsAllQuestions() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server.createContext("/api.php", ex -> {
            rawQuery.set(ex.getRequestURI().getRawQuery());
            byte[] bytes = SAMPLE_BATCH_OF_THREE.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        var got = client.fetchBatch(TriviaFilter.any(), 3);
        assertEquals(3, got.size());
        assertEquals("X1", got.get(0).correctAnswer());
        assertEquals(TriviaQuestion.Type.BOOLEAN, got.get(1).type());
        assertEquals("X3", got.get(2).correctAnswer());
        assertTrue(rawQuery.get().contains("amount=3"), () -> "got: " + rawQuery.get());
    }

    @Test
    void fetchBatchTooFewResultsThrowsWithFriendlyMessage() {
        // amount=5 but server only returns 3 — the client refuses rather
        // than silently shrinking the game.
        serve("/api.php", 200, SAMPLE_BATCH_OF_THREE);
        var ex = assertThrows(TriviaClient.TriviaException.class,
                () -> client.fetchBatch(TriviaFilter.any(), 5));
        assertTrue(ex.getMessage().toLowerCase().contains("only had"), () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("5"), () -> ex.getMessage());
    }

    @Test
    void fetchBatchRespectsAmountLimits() {
        assertThrows(TriviaClient.TriviaException.class,
                () -> client.fetchBatch(TriviaFilter.any(), 0));
        assertThrows(TriviaClient.TriviaException.class,
                () -> client.fetchBatch(TriviaFilter.any(), 51));
    }

    @Test
    void fetchSingleDelegatesToBatchOfOne() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server.createContext("/api.php", ex -> {
            rawQuery.set(ex.getRequestURI().getRawQuery());
            byte[] bytes = SAMPLE_MULTIPLE.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        client.fetch(TriviaFilter.any());
        assertTrue(rawQuery.get().contains("amount=1"), () -> "got: " + rawQuery.get());
    }
}
