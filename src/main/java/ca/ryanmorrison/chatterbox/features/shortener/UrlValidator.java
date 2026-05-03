package ca.ryanmorrison.chatterbox.features.shortener;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * URL parsing + protocol gate. Accepts only absolute http(s) URLs with a host
 * component; everything else is rejected so we never store junk like
 * {@code javascript:...} or relative paths.
 */
final class UrlValidator {

    private UrlValidator() {}

    static boolean isValidHttpUrl(String raw) {
        if (raw == null || raw.isBlank()) return false;
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (URISyntaxException e) {
            return false;
        }
        if (!uri.isAbsolute()) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) return false;
        String host = uri.getHost();
        return host != null && !host.isBlank();
    }
}
