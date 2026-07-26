package ca.ryanmorrison.chatterbox.common.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Pre-flight validation for user-supplied URLs the bot will fetch on a
 * caller's behalf.
 *
 * <p>Two stages:
 * <ol>
 *   <li>{@link #parse} — syntactic checks (well-formed, allowed scheme,
 *       length cap). Result is a sanitised {@link URI} ready for the HTTP
 *       client, or a {@link ParsedUrl.Rejected} explaining the reject
 *       reason.</li>
 *   <li>{@link #resolve} — DNS resolution plus an SSRF deny-list. Any
 *       resolved address that's loopback, link-local, site-local
 *       (RFC 1918), carrier-grade NAT, IETF protocol assignment,
 *       benchmarking, IPv6 unique-local, multicast, or the wildcard fails
 *       the check. <em>All</em> resolved addresses must pass, so a hostname
 *       that resolves to both a public and a private address is rejected —
 *       defence in depth against split-horizon resolvers and DNS
 *       rebinding.</li>
 * </ol>
 *
 * <p>Validating the URL is only half the job: an allowed host can still
 * redirect to a denied one. Callers must either disable redirects or route
 * them through {@link SafeHttp}, which re-runs both stages on every hop.
 *
 * <p>The TOCTOU gap between this resolve and the HttpClient's own resolve
 * is a known but low-severity concern: an attacker would have to time DNS
 * responses across two lookups. Acceptable for a Discord bot; if that ever
 * changes, switch to connecting via the resolved IP and setting a
 * {@code Host:} header explicitly.
 */
public final class UrlGuard {

    public static final int MAX_URL_LENGTH = 2048;
    public static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private UrlGuard() {}

    /** Result of {@link #parse}: either a usable URI or a rejection reason. */
    public sealed interface ParsedUrl {
        record Ok(URI uri) implements ParsedUrl {}
        record Rejected(String reason) implements ParsedUrl {}
    }

    /** Result of {@link #resolve}: either pass-through or a rejection. */
    public sealed interface ResolvedUrl {
        record Ok() implements ResolvedUrl {}
        record DnsFailure(String host) implements ResolvedUrl {}
        record Disallowed(String reason) implements ResolvedUrl {}
    }

    public static ParsedUrl parse(String raw) {
        if (raw == null) return new ParsedUrl.Rejected("URL is required.");
        String trimmed = raw.trim();
        // Discord users often paste URLs wrapped in <> to suppress embeds.
        if (trimmed.startsWith("<") && trimmed.endsWith(">") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) return new ParsedUrl.Rejected("URL is empty.");
        // Bare hostnames like "reddit.com" are common; default to https. Anything
        // already containing "://" keeps its scheme — including disallowed ones,
        // which the scheme check below rejects with a more useful message than
        // silently coercing them to https.
        if (!trimmed.contains("://")) {
            trimmed = "https://" + trimmed;
        }
        if (trimmed.length() > MAX_URL_LENGTH) {
            return new ParsedUrl.Rejected("URL is too long (max " + MAX_URL_LENGTH + " characters).");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return new ParsedUrl.Rejected("not a parseable URL.");
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return new ParsedUrl.Rejected("missing scheme — include `http://` or `https://`.");
        }
        if (!ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            return new ParsedUrl.Rejected("scheme `" + scheme + "` isn't supported (use `http` or `https`).");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return new ParsedUrl.Rejected("missing hostname.");
        }
        return new ParsedUrl.Ok(uri);
    }

    /**
     * Resolves the URL's host and rejects any address in a deny-listed range.
     * Returns {@link ResolvedUrl.Ok} only when <em>every</em> resolved address
     * passes.
     */
    public static ResolvedUrl resolve(URI uri) {
        return resolve(uri, UrlGuard::lookup);
    }

    /** Test seam: lets unit tests inject fake DNS results without hitting the network. */
    public static ResolvedUrl resolve(URI uri, java.util.function.Function<String, InetAddress[]> resolver) {
        String host = uri.getHost();
        InetAddress[] addresses;
        try {
            addresses = resolver.apply(host);
        } catch (DnsFailureException e) {
            return new ResolvedUrl.DnsFailure(host);
        }
        if (addresses == null || addresses.length == 0) {
            return new ResolvedUrl.DnsFailure(host);
        }
        for (InetAddress addr : addresses) {
            String reason = denyReason(addr);
            if (reason != null) {
                return new ResolvedUrl.Disallowed(host + " resolves to " + addr.getHostAddress()
                        + " (" + reason + ").");
            }
        }
        return new ResolvedUrl.Ok();
    }

    /**
     * Returns the deny-list category if {@code addr} is forbidden, or
     * {@code null} if the address is acceptable. Each category corresponds
     * to a real-world SSRF target: loopback (own host), link-local
     * (cloud-metadata services like {@code 169.254.169.254}), site-local
     * (corporate intranets), carrier-grade NAT (ISP-internal space that
     * routes but shouldn't be probed), IETF protocol assignments and
     * benchmarking ranges (neither is legitimate fetch territory), IPv6 ULA
     * (the IPv6 equivalent of RFC 1918), multicast and wildcard
     * (administrative addresses).
     *
     * <p>Decimal ({@code 2130706433}) and IPv4-mapped-IPv6
     * ({@code [::ffff:127.0.0.1]}) spellings need no special handling —
     * {@link InetAddress} normalises both before this method sees them.
     */
    public static String denyReason(InetAddress addr) {
        if (addr.isAnyLocalAddress())   return "wildcard address";
        if (addr.isLoopbackAddress())   return "loopback address";
        if (addr.isLinkLocalAddress())  return "link-local address";
        if (addr.isSiteLocalAddress())  return "private (RFC 1918) address";
        if (addr.isMulticastAddress())  return "multicast address";
        if (isIpv6UniqueLocal(addr))    return "IPv6 unique-local address";

        byte[] v4 = ipv4Bytes(addr);
        if (v4 != null) {
            int a = v4[0] & 0xFF;
            int b = v4[1] & 0xFF;
            // 100.64.0.0/10 — carrier-grade NAT (RFC 6598).
            if (a == 100 && b >= 64 && b <= 127) return "carrier-grade NAT address";
            // 192.0.0.0/24 — IETF protocol assignments (RFC 6890).
            if (a == 192 && b == 0 && (v4[2] & 0xFF) == 0) return "IETF protocol assignment address";
            // 198.18.0.0/15 — benchmarking (RFC 2544).
            if (a == 198 && (b == 18 || b == 19)) return "benchmarking address";
        }
        return null;
    }

    /**
     * IPv6 unique-local addresses (fc00::/7) aren't covered by
     * {@link InetAddress#isSiteLocalAddress()} — that method only matches
     * the deprecated site-local prefix {@code fec0::/10}. Check the first
     * byte directly: ULA addresses have the high 7 bits {@code 1111110}.
     */
    private static boolean isIpv6UniqueLocal(InetAddress addr) {
        if (!(addr instanceof Inet6Address)) return false;
        byte[] bytes = addr.getAddress();
        return (bytes[0] & 0xFE) == 0xFC;
    }

    /**
     * The four IPv4 bytes of {@code addr}, or null if it isn't IPv4. Handles
     * the IPv4-mapped IPv6 form ({@code ::ffff:a.b.c.d}) too — Java usually
     * hands those back as {@link Inet4Address} already, but an
     * {@link Inet6Address} carrying a mapped address would otherwise skip
     * every IPv4 range check below.
     */
    private static byte[] ipv4Bytes(InetAddress addr) {
        if (addr instanceof Inet4Address) return addr.getAddress();
        byte[] bytes = addr.getAddress();
        if (bytes.length != 16) return null;
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) return null;
        }
        if ((bytes[10] & 0xFF) != 0xFF || (bytes[11] & 0xFF) != 0xFF) return null;
        return new byte[] { bytes[12], bytes[13], bytes[14], bytes[15] };
    }

    /**
     * The production resolver: a real DNS lookup. Exposed so callers that
     * thread a resolver through several layers (see {@link SafeHttp}) have a
     * default to pass, and so tests can substitute a fake one.
     *
     * @throws DnsFailureException if the host doesn't resolve
     */
    public static InetAddress[] systemResolver(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new DnsFailureException();
        }
    }

    private static InetAddress[] lookup(String host) {
        return systemResolver(host);
    }

    /** Internal sentinel; never escapes this class. */
    public static final class DnsFailureException extends RuntimeException {
        public DnsFailureException() { super(null, null, false, false); }
    }
}
