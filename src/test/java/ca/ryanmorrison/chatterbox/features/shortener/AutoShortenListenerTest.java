package ca.ryanmorrison.chatterbox.features.shortener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Listener-side coverage is limited to the pure helpers — JDA's event
 * surface isn't worth mocking for the side-effecting bits, which are
 * exercised on staging.
 */
class AutoShortenListenerTest {

    @Test
    void recognisesUrlsAlreadyOnConfiguredBase() {
        assertTrue(AutoShortenListener.isAlreadyShort(
                "https://example.com/abc123", "https://example.com"));
        assertTrue(AutoShortenListener.isAlreadyShort(
                "https://example.com/abc123", "https://example.com/"));
    }

    @Test
    void caseInsensitiveOnHostAndScheme() {
        assertTrue(AutoShortenListener.isAlreadyShort(
                "HTTPS://EXAMPLE.COM/AbC123", "https://example.com"));
    }

    @Test
    void rejectsUrlsOnDifferentHost() {
        assertFalse(AutoShortenListener.isAlreadyShort(
                "https://other.example/abc123", "https://example.com"));
    }

    @Test
    void rejectsHostnameSubstringMatches() {
        // "https://example.com.attacker.test/..." starts with "https://example.com"
        // textually but is on a different host. The trailing slash on the
        // normalised base prevents a false positive.
        assertFalse(AutoShortenListener.isAlreadyShort(
                "https://example.com.attacker.test/abc123", "https://example.com"));
    }
}
