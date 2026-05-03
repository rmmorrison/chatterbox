package ca.ryanmorrison.chatterbox.features.shortener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlValidatorTest {

    @Test
    void acceptsHttpAndHttps() {
        assertTrue(UrlValidator.isValidHttpUrl("http://example.com"));
        assertTrue(UrlValidator.isValidHttpUrl("https://example.com/path?q=1"));
        assertTrue(UrlValidator.isValidHttpUrl("HTTPS://EXAMPLE.COM"));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertTrue(UrlValidator.isValidHttpUrl("  https://example.com  "));
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertFalse(UrlValidator.isValidHttpUrl("ftp://example.com"));
        assertFalse(UrlValidator.isValidHttpUrl("javascript:alert(1)"));
        assertFalse(UrlValidator.isValidHttpUrl("file:///etc/passwd"));
    }

    @Test
    void rejectsRelativeOrSchemeless() {
        assertFalse(UrlValidator.isValidHttpUrl("example.com"));
        assertFalse(UrlValidator.isValidHttpUrl("/foo/bar"));
    }

    @Test
    void rejectsBlankAndNull() {
        assertFalse(UrlValidator.isValidHttpUrl(null));
        assertFalse(UrlValidator.isValidHttpUrl(""));
        assertFalse(UrlValidator.isValidHttpUrl("   "));
    }

    @Test
    void rejectsMalformed() {
        assertFalse(UrlValidator.isValidHttpUrl("https://"));
        assertFalse(UrlValidator.isValidHttpUrl("http://[bad"));
    }
}
