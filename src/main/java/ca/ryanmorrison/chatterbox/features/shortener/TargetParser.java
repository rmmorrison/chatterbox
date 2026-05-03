package ca.ryanmorrison.chatterbox.features.shortener;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses the {@code target} option for {@code /shorten delete} and
 * {@code /shorten peek} into a token. Accepts either:
 * <ul>
 *   <li>a bare 6-char base36 token (e.g. {@code "abc123"}), or</li>
 *   <li>a full short URL whose prefix matches the bot's configured base
 *       (e.g. {@code "https://example.com/abc123"}).</li>
 * </ul>
 * Lookup by the original destination URL is intentionally not supported —
 * destinations may be ambiguous or sensitive.
 */
final class TargetParser {

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("^[a-z0-9]{" + TokenGenerator.LENGTH + "}$");

    private TargetParser() {}

    /**
     * @param target  user-supplied input (may be null/blank)
     * @param baseUrl the configured short URL prefix; used to recognise full
     *                short URLs. Trailing slash optional.
     * @return the token if recognised, or empty.
     */
    static Optional<String> extractToken(String target, String baseUrl) {
        if (target == null) return Optional.empty();
        String t = target.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return Optional.empty();

        if (TOKEN_PATTERN.matcher(t).matches()) {
            return Optional.of(t);
        }

        if (baseUrl != null) {
            String prefix = baseUrl.trim().toLowerCase(Locale.ROOT);
            if (!prefix.endsWith("/")) prefix = prefix + "/";
            if (t.startsWith(prefix)) {
                String tail = t.substring(prefix.length());
                // Token must be the next path segment; tolerate trailing slash,
                // query string, or fragment — but they shouldn't be part of the token.
                int cut = tail.length();
                for (int i = 0; i < tail.length(); i++) {
                    char c = tail.charAt(i);
                    if (c == '/' || c == '?' || c == '#') { cut = i; break; }
                }
                String candidate = tail.substring(0, cut);
                if (TOKEN_PATTERN.matcher(candidate).matches()) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }
}
