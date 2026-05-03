package ca.ryanmorrison.chatterbox.features.shortener;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortenerRedirectHandlerTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 3, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void rendersOgTagsAndRefreshMeta() {
        var entry = new ShortenedUrl(1L, "abc123", "https://example.com/path",
                42L, NOW,
                new OpenGraphMetadata("Hello", "Greetings.",
                        "https://example.com/img.png", "Example"));
        String html = ShortenerRedirectHandler.renderHtml(entry);

        assertTrue(html.contains("<meta http-equiv=\"refresh\" content=\"0; url=https://example.com/path\">"));
        assertTrue(html.contains("<meta property=\"og:title\" content=\"Hello\">"));
        assertTrue(html.contains("<meta property=\"og:description\" content=\"Greetings.\">"));
        assertTrue(html.contains("<meta property=\"og:image\" content=\"https://example.com/img.png\">"));
        assertTrue(html.contains("<meta property=\"og:site_name\" content=\"Example\">"));
        assertTrue(html.contains("<meta name=\"twitter:card\" content=\"summary_large_image\">"));
        assertTrue(html.contains("location.replace(\"https://example.com/path\")"));
        assertTrue(html.contains("<title>Hello</title>"));
    }

    @Test
    void omitsAbsentOgTags() {
        var entry = new ShortenedUrl(1L, "blank1", "https://example.com/", 1L, NOW, OpenGraphMetadata.EMPTY);
        String html = ShortenerRedirectHandler.renderHtml(entry);

        assertFalse(html.contains("og:title"));
        assertFalse(html.contains("og:description"));
        assertFalse(html.contains("og:image"));
        assertFalse(html.contains("og:site_name"));
        assertTrue(html.contains("<meta name=\"twitter:card\" content=\"summary\">"));
        assertTrue(html.contains("<title>Redirecting"));
    }

    @Test
    void escapesHostileHtmlInTargetUrl() {
        var entry = new ShortenedUrl(1L, "x", "https://example.com/?q=<script>alert(1)</script>",
                1L, NOW, OpenGraphMetadata.EMPTY);
        String html = ShortenerRedirectHandler.renderHtml(entry);

        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    @Test
    void escapesHostileHtmlInOgTitle() {
        var entry = new ShortenedUrl(1L, "x", "https://example.com/", 1L, NOW,
                new OpenGraphMetadata("\"><script>alert(1)</script>", null, null, null));
        String html = ShortenerRedirectHandler.renderHtml(entry);

        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&quot;&gt;&lt;script&gt;"));
    }

    @Test
    void escapesJsBreakoutInRedirectScript() {
        var entry = new ShortenedUrl(1L, "x", "https://example.com/\"+alert(1)+\"",
                1L, NOW, OpenGraphMetadata.EMPTY);
        String html = ShortenerRedirectHandler.renderHtml(entry);

        // Quote characters in the URL must be backslash-escaped inside the JS
        // string literal so they can't terminate it and inject script.
        assertTrue(html.contains("location.replace(\"https://example.com/\\\"+alert(1)+\\\"\")"));
    }

    @Test
    void escapeAttrEncodesAllDangerousChars() {
        String s = ShortenerRedirectHandler.escapeAttr("a&b<c>d\"e'f");
        assertTrue(s.equals("a&amp;b&lt;c&gt;d&quot;e&#39;f"));
    }
}
