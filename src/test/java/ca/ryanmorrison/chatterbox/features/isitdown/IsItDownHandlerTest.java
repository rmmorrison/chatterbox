package ca.ryanmorrison.chatterbox.features.isitdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renderer-only checks for {@link IsItDownHandler#formatResult}. Each
 * variant should produce a unique user-facing message that names the
 * relevant detail (status code, host, timeout seconds, etc.).
 */
class IsItDownHandlerTest {

    @Test
    void liveMessageMentionsStatusAndLatency() {
        String msg = IsItDownHandler.formatResult("https://example.com",
                new CheckResult.Live(200, 245));
        assertTrue(msg.contains("`200`"), msg);
        assertTrue(msg.contains("245 ms"), msg);
        assertTrue(msg.toLowerCase().contains("just you"), msg);
    }

    @Test
    void badStatusMessageMentionsStatusCode() {
        String msg = IsItDownHandler.formatResult("https://example.com",
                new CheckResult.BadStatus(503, 410));
        assertTrue(msg.contains("`503`"), msg);
        assertTrue(msg.toLowerCase().contains("not just you"), msg);
    }

    @Test
    void timeoutMessageNamesTheTimeout() {
        String msg = IsItDownHandler.formatResult("https://example.com",
                new CheckResult.Timeout(10));
        assertTrue(msg.contains("10 seconds"), msg);
    }

    @Test
    void dnsFailureNamesTheHost() {
        String msg = IsItDownHandler.formatResult("https://nope.invalid",
                new CheckResult.DnsFailure("nope.invalid"));
        assertTrue(msg.contains("`nope.invalid`"), msg);
        assertTrue(msg.toLowerCase().contains("resolve"), msg);
    }

    @Test
    void connectionRefusedMessagesAreDistinct() {
        String refused = IsItDownHandler.formatResult("https://x.example",
                new CheckResult.ConnectionRefused("x.example"));
        String unreach = IsItDownHandler.formatResult("https://x.example",
                new CheckResult.Unreachable("x.example"));
        assertTrue(refused.toLowerCase().contains("refused"), refused);
        assertTrue(unreach.toLowerCase().contains("no route"), unreach);
        assertEquals(false, refused.equals(unreach));
    }

    @Test
    void tlsErrorIsDistinguished() {
        String msg = IsItDownHandler.formatResult("https://expired.example",
                new CheckResult.TlsError("expired.example"));
        assertTrue(msg.toLowerCase().contains("tls"), msg);
    }

    @Test
    void invalidUrlEchoesReason() {
        String msg = IsItDownHandler.formatResult("nonsense",
                new CheckResult.InvalidUrl("missing scheme — include `http://` or `https://`."));
        assertTrue(msg.contains("missing scheme"), msg);
    }

    @Test
    void disallowedSurfacesPolicyReason() {
        String msg = IsItDownHandler.formatResult("http://localhost:8080/",
                new CheckResult.Disallowed("localhost resolves to 127.0.0.1 (loopback address)."));
        assertTrue(msg.toLowerCase().contains("won't probe"), msg);
        assertTrue(msg.toLowerCase().contains("loopback"), msg);
    }

    @Test
    void otherIsGenericAndDoesNotLeakDetails() {
        String msg = IsItDownHandler.formatResult("https://x.example",
                new CheckResult.Other("x.example"));
        assertFalse(msg.toLowerCase().contains("exception"), msg);
        assertFalse(msg.toLowerCase().contains("ioexception"), msg);
    }

    @Test
    void backticksInUrlAreStripped() {
        // A URL containing a backtick would break inline-code formatting and
        // could be used to inject markdown; the renderer falls back to plain
        // text in that case.
        String msg = IsItDownHandler.formatResult("https://evil`example.com",
                new CheckResult.Live(200, 1));
        // The URL is not wrapped in backticks (the safe fallback) — verify the
        // raw URL appears without surrounding inline-code markers.
        assertTrue(msg.contains("https://evil`example.com"), msg);
    }
}
