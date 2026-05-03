package ca.ryanmorrison.chatterbox.features.shortener;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TargetParserTest {

    private static final String BASE_NO_SLASH    = "https://example.com";
    private static final String BASE_WITH_SLASH  = "https://example.com/";
    private static final String BASE_WITH_PATH   = "https://example.com/links/";

    @Test
    void acceptsBareToken() {
        assertEquals(Optional.of("abc123"), TargetParser.extractToken("abc123", BASE_NO_SLASH));
    }

    @Test
    void lowercasesBareToken() {
        assertEquals(Optional.of("abc123"), TargetParser.extractToken("ABC123", BASE_NO_SLASH));
    }

    @Test
    void trimsWhitespace() {
        assertEquals(Optional.of("abc123"), TargetParser.extractToken("  abc123  ", BASE_NO_SLASH));
    }

    @Test
    void rejectsWrongLengthToken() {
        assertFalse(TargetParser.extractToken("abc12", BASE_NO_SLASH).isPresent());
        assertFalse(TargetParser.extractToken("abc1234", BASE_NO_SLASH).isPresent());
    }

    @Test
    void rejectsNonAlphanumericToken() {
        assertFalse(TargetParser.extractToken("abc-12", BASE_NO_SLASH).isPresent());
        assertFalse(TargetParser.extractToken("abc 12", BASE_NO_SLASH).isPresent());
    }

    @Test
    void extractsTokenFromShortUrl() {
        assertEquals(Optional.of("abc123"),
                TargetParser.extractToken("https://example.com/abc123", BASE_NO_SLASH));
        assertEquals(Optional.of("abc123"),
                TargetParser.extractToken("https://example.com/abc123", BASE_WITH_SLASH));
    }

    @Test
    void toleratesTrailingSlashAndQueryAndFragment() {
        assertEquals(Optional.of("abc123"),
                TargetParser.extractToken("https://example.com/abc123/", BASE_NO_SLASH));
        assertEquals(Optional.of("abc123"),
                TargetParser.extractToken("https://example.com/abc123?foo=bar", BASE_NO_SLASH));
        assertEquals(Optional.of("abc123"),
                TargetParser.extractToken("https://example.com/abc123#x", BASE_NO_SLASH));
    }

    @Test
    void extractsTokenWhenBaseHasPath() {
        assertEquals(Optional.of("abc123"),
                TargetParser.extractToken("https://example.com/links/abc123", BASE_WITH_PATH));
    }

    @Test
    void rejectsShortUrlFromWrongHost() {
        assertFalse(TargetParser.extractToken("https://other.example/abc123", BASE_NO_SLASH).isPresent());
    }

    @Test
    void rejectsBareDestinationUrl() {
        assertFalse(TargetParser.extractToken("https://google.com", BASE_NO_SLASH).isPresent());
    }

    @Test
    void rejectsBlankAndNull() {
        assertFalse(TargetParser.extractToken(null, BASE_NO_SLASH).isPresent());
        assertFalse(TargetParser.extractToken("", BASE_NO_SLASH).isPresent());
        assertFalse(TargetParser.extractToken("   ", BASE_NO_SLASH).isPresent());
    }

    @Test
    void caseInsensitiveOnShortUrlScheme() {
        assertEquals(Optional.of("abc123"),
                TargetParser.extractToken("HTTPS://EXAMPLE.COM/ABC123", BASE_NO_SLASH));
    }
}
