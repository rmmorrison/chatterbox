package ca.ryanmorrison.chatterbox.features.isitdown;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlGuardTest {

    // ---- parse ----

    @Test
    void plainHttpsUrlIsAccepted() {
        var ok = (UrlGuard.ParsedUrl.Ok) UrlGuard.parse("https://example.com/foo");
        assertEquals("example.com", ok.uri().getHost());
        assertEquals("https", ok.uri().getScheme());
    }

    @Test
    void httpAlsoAccepted() {
        assertInstanceOf(UrlGuard.ParsedUrl.Ok.class, UrlGuard.parse("http://example.com"));
    }

    @Test
    void anglesAreStrippedDiscordStyle() {
        var ok = (UrlGuard.ParsedUrl.Ok) UrlGuard.parse("<https://example.com/foo>");
        assertEquals("https", ok.uri().getScheme());
        assertEquals("example.com", ok.uri().getHost());
    }

    @Test
    void leadingTrailingWhitespaceTrimmed() {
        assertInstanceOf(UrlGuard.ParsedUrl.Ok.class, UrlGuard.parse("   https://example.com  "));
    }

    @Test
    void bareHostnameDefaultsToHttps() {
        var ok = (UrlGuard.ParsedUrl.Ok) UrlGuard.parse("reddit.com");
        assertEquals("https", ok.uri().getScheme());
        assertEquals("reddit.com", ok.uri().getHost());
    }

    @Test
    void bareHostnameWithPathDefaultsToHttps() {
        var ok = (UrlGuard.ParsedUrl.Ok) UrlGuard.parse("reddit.com/r/java");
        assertEquals("https", ok.uri().getScheme());
        assertEquals("reddit.com", ok.uri().getHost());
        assertEquals("/r/java", ok.uri().getPath());
    }

    @Test
    void bareHostnameWithPortDefaultsToHttps() {
        var ok = (UrlGuard.ParsedUrl.Ok) UrlGuard.parse("example.com:8443/foo");
        assertEquals("https", ok.uri().getScheme());
        assertEquals("example.com", ok.uri().getHost());
        assertEquals(8443, ok.uri().getPort());
    }

    @Test
    void disallowedSchemeRejected() {
        var rejected = (UrlGuard.ParsedUrl.Rejected) UrlGuard.parse("ftp://example.com");
        assertTrue(rejected.reason().toLowerCase().contains("scheme"));
    }

    @Test
    void fileSchemeRejected() {
        assertInstanceOf(UrlGuard.ParsedUrl.Rejected.class, UrlGuard.parse("file:///etc/passwd"));
    }

    @Test
    void emptyAndBlankRejected() {
        assertInstanceOf(UrlGuard.ParsedUrl.Rejected.class, UrlGuard.parse(""));
        assertInstanceOf(UrlGuard.ParsedUrl.Rejected.class, UrlGuard.parse("   "));
        assertInstanceOf(UrlGuard.ParsedUrl.Rejected.class, UrlGuard.parse(null));
    }

    @Test
    void overlyLongUrlRejected() {
        StringBuilder sb = new StringBuilder("https://example.com/");
        sb.append("x".repeat(UrlGuard.MAX_URL_LENGTH));
        var rejected = (UrlGuard.ParsedUrl.Rejected) UrlGuard.parse(sb.toString());
        assertTrue(rejected.reason().toLowerCase().contains("long"));
    }

    @Test
    void hostlessUriRejected() {
        var rejected = (UrlGuard.ParsedUrl.Rejected) UrlGuard.parse("https:///path");
        assertNotNull(rejected.reason());
    }

    @Test
    void unparseableUriRejected() {
        assertInstanceOf(UrlGuard.ParsedUrl.Rejected.class, UrlGuard.parse("https://exa mple.com/"));
    }

    // ---- denyReason (SSRF) ----

    @Test
    void publicIpv4Allowed() throws Exception {
        // 1.1.1.1 — Cloudflare DNS, definitely public.
        assertNull(UrlGuard.denyReason(InetAddress.getByName("1.1.1.1")));
    }

    @Test
    void loopbackRejected() throws Exception {
        assertEquals("loopback address",
                UrlGuard.denyReason(InetAddress.getByName("127.0.0.1")));
        assertEquals("loopback address",
                UrlGuard.denyReason(InetAddress.getByName("::1")));
    }

    @Test
    void rfc1918Rejected() throws Exception {
        assertEquals("private (RFC 1918) address",
                UrlGuard.denyReason(InetAddress.getByName("10.0.0.1")));
        assertEquals("private (RFC 1918) address",
                UrlGuard.denyReason(InetAddress.getByName("192.168.1.1")));
        assertEquals("private (RFC 1918) address",
                UrlGuard.denyReason(InetAddress.getByName("172.16.5.5")));
    }

    @Test
    void linkLocalRejected() throws Exception {
        // Cloud-metadata service — the canonical SSRF target.
        assertEquals("link-local address",
                UrlGuard.denyReason(InetAddress.getByName("169.254.169.254")));
    }

    @Test
    void wildcardRejected() throws Exception {
        assertEquals("wildcard address",
                UrlGuard.denyReason(InetAddress.getByName("0.0.0.0")));
    }

    @Test
    void multicastRejected() throws Exception {
        assertEquals("multicast address",
                UrlGuard.denyReason(InetAddress.getByName("224.0.0.1")));
    }

    @Test
    void ipv6UniqueLocalRejected() throws Exception {
        // fc00::/7 ULA — not caught by isSiteLocalAddress(), needs the explicit check.
        assertEquals("IPv6 unique-local address",
                UrlGuard.denyReason(InetAddress.getByName("fc00::1")));
        assertEquals("IPv6 unique-local address",
                UrlGuard.denyReason(InetAddress.getByName("fd12:3456:789a::1")));
    }

    @Test
    void ipv6LinkLocalRejected() throws Exception {
        assertEquals("link-local address",
                UrlGuard.denyReason(InetAddress.getByName("fe80::1")));
    }

    // ---- resolve ----

    @Test
    void resolveReturnsOkForPublicAddress() throws Exception {
        var uri = ((UrlGuard.ParsedUrl.Ok) UrlGuard.parse("https://example.com/")).uri();
        InetAddress[] result = {InetAddress.getByName("93.184.216.34")};
        var resolved = UrlGuard.resolve(uri, host -> result);
        assertInstanceOf(UrlGuard.ResolvedUrl.Ok.class, resolved);
    }

    @Test
    void resolveReturnsDisallowedWhenAnyAddressIsPrivate() throws Exception {
        var uri = ((UrlGuard.ParsedUrl.Ok) UrlGuard.parse("https://mixed.example/")).uri();
        InetAddress[] result = {
                InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("10.0.0.1") // one bad apple → reject
        };
        var resolved = UrlGuard.resolve(uri, host -> result);
        var disallowed = (UrlGuard.ResolvedUrl.Disallowed) resolved;
        assertTrue(disallowed.reason().contains("private"));
    }

    @Test
    void resolveReturnsDnsFailureOnUnknownHost() throws Exception {
        var uri = ((UrlGuard.ParsedUrl.Ok) UrlGuard.parse("https://nope.example/")).uri();
        var resolved = UrlGuard.resolve(uri, host -> { throw new UrlGuard.DnsFailureException(); });
        assertInstanceOf(UrlGuard.ResolvedUrl.DnsFailure.class, resolved);
    }

    @Test
    void resolveTreatsEmptyResultAsDnsFailure() throws Exception {
        var uri = ((UrlGuard.ParsedUrl.Ok) UrlGuard.parse("https://nada.example/")).uri();
        var resolved = UrlGuard.resolve(uri, host -> new InetAddress[0]);
        assertInstanceOf(UrlGuard.ResolvedUrl.DnsFailure.class, resolved);
    }
}
