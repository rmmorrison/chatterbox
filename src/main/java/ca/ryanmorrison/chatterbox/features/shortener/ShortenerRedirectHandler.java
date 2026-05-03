package ca.ryanmorrison.chatterbox.features.shortener;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;

import java.util.Locale;
import java.util.Optional;

/**
 * Javalin handler for {@code GET /{token}}.
 *
 * <p>Renders an HTML interstitial page rather than a bare 30x so social-card
 * crawlers (Discord, Slack, Twitter) pick up OpenGraph metadata captured at
 * shorten time. Browsers see a {@code <meta http-equiv="refresh">} (instant)
 * with a JavaScript fallback and a manual link, so the redirect feels
 * indistinguishable from a server-side 301.
 *
 * <p>404 on miss. Token lookups are case-insensitive — the alphabet is
 * lowercase but human-pasted links may have mangled case.
 */
final class ShortenerRedirectHandler implements Handler {

    static final String PATH = "/{token}";
    static final String PATH_PARAM = "token";

    private static final char LINE_SEPARATOR     = 0x2028;
    private static final char PARAGRAPH_SEPARATOR = 0x2029;

    private final ShortenerRepository repository;

    ShortenerRedirectHandler(ShortenerRepository repository) {
        this.repository = repository;
    }

    @Override
    public void handle(Context ctx) {
        String token = ctx.pathParam(PATH_PARAM).toLowerCase(Locale.ROOT);

        Optional<ShortenedUrl> match = repository.findByToken(token);
        if (match.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result("Not found.");
            return;
        }

        ShortenedUrl entry = match.get();
        ctx.status(HttpStatus.OK)
                .contentType("text/html; charset=utf-8")
                .header("Cache-Control", "no-store")
                .result(renderHtml(entry));
    }

    static String renderHtml(ShortenedUrl entry) {
        String url = entry.url();
        OpenGraphMetadata m = entry.metadata() == null ? OpenGraphMetadata.EMPTY : entry.metadata();

        StringBuilder sb = new StringBuilder(2048);
        sb.append("<!doctype html>\n<html lang=\"en\"><head>\n")
          .append("<meta charset=\"utf-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
          .append("<meta name=\"robots\" content=\"noindex,nofollow\">\n")
          .append("<meta http-equiv=\"refresh\" content=\"0; url=").append(escapeAttr(url)).append("\">\n");

        appendMeta(sb, "og:url", url);
        appendMeta(sb, "og:type", "website");
        appendMeta(sb, "og:title", m.title());
        appendMeta(sb, "og:description", m.description());
        appendMeta(sb, "og:image", m.image());
        appendMeta(sb, "og:site_name", m.siteName());
        appendMeta(sb, "twitter:card", m.image() != null ? "summary_large_image" : "summary");
        appendMeta(sb, "twitter:title", m.title());
        appendMeta(sb, "twitter:description", m.description());
        appendMeta(sb, "twitter:image", m.image());

        String pageTitle = m.title() != null ? m.title() : "Redirecting…";
        sb.append("<title>").append(escapeText(pageTitle)).append("</title>\n");
        sb.append("</head><body>\n");
        sb.append("<p>Redirecting to <a href=\"").append(escapeAttr(url)).append("\">")
          .append(escapeText(url)).append("</a>…</p>\n");
        sb.append("<script>location.replace(").append(escapeJsString(url)).append(");</script>\n");
        sb.append("</body></html>\n");
        return sb.toString();
    }

    private static void appendMeta(StringBuilder sb, String property, String content) {
        if (content == null || content.isBlank()) return;
        String attr = property.startsWith("og:") ? "property" : "name";
        sb.append("<meta ").append(attr).append("=\"").append(property)
          .append("\" content=\"").append(escapeAttr(content)).append("\">\n");
    }

    static String escapeAttr(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> out.append("&amp;");
                case '<'  -> out.append("&lt;");
                case '>'  -> out.append("&gt;");
                case '"'  -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default   -> out.append(c);
            }
        }
        return out.toString();
    }

    static String escapeText(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default  -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Builds a JavaScript string literal — surrounding double quotes plus
     * escapes for characters that would let an attacker break out of the
     * string and inject script. The redirect target may be user-controlled
     * (the URL the original /shorten call was given), so this can't trust
     * the input. U+2028 / U+2029 are line terminators in JS string literals
     * even though they aren't in HTML, so they need escaping too.
     */
    static String escapeJsString(String s) {
        if (s == null) return "\"\"";
        StringBuilder out = new StringBuilder(s.length() + 2);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"'  -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<'  -> out.append("\\u003c");
                case '>'  -> out.append("\\u003e");
                case '&'  -> out.append("\\u0026");
                case LINE_SEPARATOR      -> out.append("\\u2028");
                case PARAGRAPH_SEPARATOR -> out.append("\\u2029");
                default   -> out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }
}
