package ca.ryanmorrison.chatterbox.features.shortener;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;

import java.util.Locale;
import java.util.Optional;

/**
 * Javalin handler for {@code GET /{token}}. On hit, returns 301 with the
 * original URL in the {@code Location} header plus a tiny HTML body
 * carrying an anchor to the destination — matches the shape bit.ly serves,
 * which Discord's link-preview crawler unfurls correctly. On miss, 404.
 *
 * <p>Tokens are looked up in lowercase since the alphabet is case-insensitive.
 */
final class ShortenerRedirectHandler implements Handler {

    static final String PATH = "/{token}";
    static final String PATH_PARAM = "token";

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
        String url = match.get().url();
        ctx.status(HttpStatus.MOVED_PERMANENTLY)
                .header("Location", url)
                .contentType("text/html; charset=utf-8")
                .result("<html>\n<body><a href=\"" + escapeAttr(url) + "\">moved here</a></body>\n");
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
}
