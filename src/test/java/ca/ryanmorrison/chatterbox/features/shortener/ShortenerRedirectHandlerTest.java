package ca.ryanmorrison.chatterbox.features.shortener;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortenerRedirectHandlerTest {

    @Test
    void escapeAttrEncodesAllDangerousChars() {
        assertEquals("a&amp;b&lt;c&gt;d&quot;e&#39;f",
                ShortenerRedirectHandler.escapeAttr("a&b<c>d\"e'f"));
    }

    @Test
    void escapeAttrLeavesSafeStringsAlone() {
        assertEquals("https://example.com/abc123",
                ShortenerRedirectHandler.escapeAttr("https://example.com/abc123"));
    }

    @Test
    void escapeAttrTolersNull() {
        assertEquals("", ShortenerRedirectHandler.escapeAttr(null));
    }

    @Test
    void escapeAttrPreventsScriptInjection() {
        String hostile = "https://example.com/?q=<script>alert(1)</script>";
        String escaped = ShortenerRedirectHandler.escapeAttr(hostile);
        assertFalse(escaped.contains("<script>"));
        assertTrue(escaped.contains("&lt;script&gt;"));
    }

    // -- end-to-end click counter behaviour ---------------------------------
    //
    // These tests stand up a real SQLite-backed repository and a Javalin
    // server on an ephemeral port, then exercise the handler over HTTP. We
    // care about the user-visible contract:
    //   - live redirects bump click_count and stamp last_clicked_at;
    //   - 410 Gone (deleted) and 404 (unknown) paths must not increment.

    private static final long USER = 4242L;
    private static final long MOD = 9999L;
    private static final OffsetDateTime CLICK_TIME =
            OffsetDateTime.of(2026, 5, 9, 20, 0, 0, 0, ZoneOffset.UTC);

    private Path dbFile;
    private HikariDataSource dataSource;
    private DSLContext dsl;
    private ShortenerRepository repo;
    private Javalin app;
    private HttpClient http;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("chatterbox-redirect-test", ".db");
        Files.delete(dbFile);

        var hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:sqlite:" + dbFile);
        hc.setMaximumPoolSize(1);
        hc.setConnectionInitSql("PRAGMA foreign_keys = ON");
        dataSource = new HikariDataSource(hc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/shortener/sqlite")
                .load()
                .migrate();

        dsl = DSL.using(dataSource, SQLDialect.SQLITE, new Settings().withRenderSchema(false));
        repo = new ShortenerRepository(dsl);

        Clock fixed = Clock.fixed(CLICK_TIME.toInstant(), ZoneOffset.UTC);
        var handler = new ShortenerRedirectHandler(repo, fixed);
        app = Javalin.create(cfg -> cfg.routes.get(ShortenerRedirectHandler.PATH, handler));
        app.start(0);

        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (app != null) app.stop();
        if (dataSource != null) dataSource.close();
        if (dbFile != null) Files.deleteIfExists(dbFile);
    }

    private HttpResponse<String> get(String token) throws Exception {
        URI uri = URI.create("http://localhost:" + app.port() + "/" + token);
        return http.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void liveTokenRedirectsAndIncrementsCounter() throws Exception {
        long id = repo.insert("abc123", "https://example.com/x", USER, CLICK_TIME).orElseThrow().id();

        HttpResponse<String> res = get("abc123");
        assertEquals(301, res.statusCode());
        assertEquals("https://example.com/x", res.headers().firstValue("Location").orElseThrow());

        ShortenedUrl after = repo.findByIdIncludingDeleted(id).orElseThrow();
        assertEquals(1L, after.clickCount());
        assertEquals(CLICK_TIME, after.lastClickedAt().orElseThrow());
    }

    @Test
    void multipleRedirectsAccumulate() throws Exception {
        long id = repo.insert("abc123", "https://example.com/x", USER, CLICK_TIME).orElseThrow().id();
        for (int i = 0; i < 3; i++) {
            assertEquals(301, get("abc123").statusCode());
        }
        assertEquals(3L, repo.findByIdIncludingDeleted(id).orElseThrow().clickCount());
    }

    @Test
    void deletedTokenReturnsGoneAndDoesNotIncrement() throws Exception {
        long id = repo.insert("abc123", "https://example.com/x", USER, CLICK_TIME).orElseThrow().id();
        repo.softDelete(id, MOD, CLICK_TIME);

        HttpResponse<String> res = get("abc123");
        assertEquals(410, res.statusCode());
        assertEquals(0L, repo.findByIdIncludingDeleted(id).orElseThrow().clickCount());
    }

    @Test
    void unknownTokenReturnsNotFound() throws Exception {
        // Pre-existing live row to confirm its counter is untouched by an
        // unrelated 404.
        long id = repo.insert("abc123", "https://example.com/x", USER, CLICK_TIME).orElseThrow().id();
        HttpResponse<String> res = get("zzzzzz");
        assertEquals(404, res.statusCode());
        assertEquals(0L, repo.findByIdIncludingDeleted(id).orElseThrow().clickCount());
    }

    @Test
    void uppercasePathIsNormalisedToLowercase() throws Exception {
        // Tokens are stored lowercase; the handler should be case-insensitive
        // so users typing the URL by hand still land on the destination AND
        // get counted.
        long id = repo.insert("abc123", "https://example.com/x", USER, CLICK_TIME).orElseThrow().id();
        assertEquals(301, get("ABC123").statusCode());
        assertEquals(1L, repo.findByIdIncludingDeleted(id).orElseThrow().clickCount());
    }
}
