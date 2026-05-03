package ca.ryanmorrison.chatterbox.features.shortener;

import org.junit.jupiter.api.Test;

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
}
