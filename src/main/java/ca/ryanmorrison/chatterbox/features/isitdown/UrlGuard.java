package ca.ryanmorrison.chatterbox.features.isitdown;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Pre-flight validation for URLs supplied to {@code /isitdown}.
 *
 * <p>Two stages:
 * <ol>
 *   <li>{@link #parse} — syntactic checks (well-formed, allowed scheme,
 *       length cap). Result is a sanitised {@link URI} ready for the HTTP
 *       client, or a {@link CheckResult.InvalidUrl} explaining the reject
 *       reason.</li>
 *   <li>{@link #resolve} — DNS resolution plus an SSRF deny-list. Any
 *       resolved address that's loopback, link-local, site-local
 *       (RFC 1918), IPv6 unique-local (fc00::/7), multicast, or the
 *       wildcard fails the check. <em>All</em> resolved addresses must
 *       pass, so a hostname that resolves to both a public and a private
 *       address is rejected — defence in depth against split-horizon
 *       resolvers and DNS rebinding.</li>
 * </ol>
 *
 * <p>The TOCTOU gap between this resolve and the HttpClient's own resolve
 * inside {@link IsItDownChecker} is a known but low-severity concern: an
 * attacker would have to time DNS responses across two lookups. Acceptable
 * for a Discord bot; if that ever changes, switch to connecting via the
 * resolved IP and setting a {@code Host:} header explicitly.
 */
final class UrlGuard {

    static final int MAX_URL_LENGTH = 2048;
    static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private UrlGuard() {}

    /** Result of {@link #parse}: either a usable URI or a rejection reason. */
    sealed interface ParsedUrl {
        record Ok(URI uri) implements ParsedUrl {}
        record Rejected(String reason) implements ParsedUrl {}
    }

    /** Result of {@link #resolve}: either pass-through or a rejection. */
    sealed interface ResolvedUrl {
        record Ok() implements ResolvedUrl {}
        record DnsFailure(String host) implements ResolvedUrl {}
        record Disallowed(String reason) implements ResolvedUrl {}
    }

    static ParsedUrl parse(String raw) {
        if (raw == null) return new ParsedUrl.Rejected("URL is required.");
        String trimmed = raw.trim();
        // Discord users often paste URLs wrapped in <> to suppress embeds.
        if (trimmed.startsWith("<") && trimmed.endsWith(">") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) return new ParsedUrl.Rejected("URL is empty.");
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
     * Resolves the URL's host and rejects any address that's loopback,
     * link-local, RFC 1918 / IPv6 ULA, multicast, or the wildcard. Returns
     * {@link ResolvedUrl.Ok} only when <em>every</em> resolved address
     * passes.
     */
    static ResolvedUrl resolve(URI uri) {
        return resolve(uri, UrlGuard::lookup);
    }

    /** Test seam: lets unit tests inject fake DNS results without hitting the network. */
    static ResolvedUrl resolve(URI uri, java.util.function.Function<String, InetAddress[]> resolver) {
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
     * (corporate intranets), IPv6 ULA (the IPv6 equivalent of RFC 1918),
     * multicast and wildcard (administrative addresses).
     */
    static String denyReason(InetAddress addr) {
        if (addr.isAnyLocalAddress())   return "wildcard address";
        if (addr.isLoopbackAddress())   return "loopback address";
        if (addr.isLinkLocalAddress())  return "link-local address";
        if (addr.isSiteLocalAddress())  return "private (RFC 1918) address";
        if (addr.isMulticastAddress())  return "multicast address";
        if (isIpv6UniqueLocal(addr))    return "IPv6 unique-local address";
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

    private static InetAddress[] lookup(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new DnsFailureException();
        }
    }

    /** Internal sentinel; never escapes the package. */
    static final class DnsFailureException extends RuntimeException {
        DnsFailureException() { super(null, null, false, false); }
    }
}
