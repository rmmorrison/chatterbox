package ca.ryanmorrison.chatterbox.features.shortener;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Javalin handler for {@code GET /{token}}.
 *
 * <p>Outcomes:
 * <ul>
 *   <li><b>Live token</b>: 301 with the original URL in the {@code Location}
 *       header plus a tiny HTML body carrying an anchor — matches bit.ly's
 *       shape, which Discord's link-preview crawler unfurls correctly.</li>
 *   <li><b>Soft-deleted token</b>: 410 Gone. Tokens are never reissued, so
 *       this is a permanent state — 410 communicates that more accurately
 *       than 404 to crawlers.</li>
 *   <li><b>Unknown token</b>: 404.</li>
 * </ul>
 *
 * <p>Tokens are looked up in lowercase since the alphabet is case-insensitive.
 */
final class ShortenerRedirectHandler implements Handler {

    static final String PATH = "/{token}";
    static final String PATH_PARAM = "token";

    private static final Logger log = LoggerFactory.getLogger(ShortenerRedirectHandler.class);

    private final ShortenerRepository repository;
    private final Clock clock;

    ShortenerRedirectHandler(ShortenerRepository repository) {
        this(repository, Clock.systemUTC());
    }

    /** Test seam — lets tests pin "now" for deterministic last_clicked_at values. */
    ShortenerRedirectHandler(ShortenerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void handle(Context ctx) {
        String token = ctx.pathParam(PATH_PARAM).toLowerCase(Locale.ROOT);

        // This route sits at the web root and is internet-facing, so it absorbs
        // every background scanner probing /wp-login.php, /.env and friends.
        // Rejecting anything that can't be a token keeps that traffic off the
        // database entirely — which matters because SQLite runs a single
        // connection shared with the bot's message handling.
        if (!isPlausibleToken(token)) {
            ctx.status(HttpStatus.NOT_FOUND).result("Not found.");
            return;
        }

        Optional<ShortenedUrl> match = repository.findByTokenIncludingDeleted(token);
        if (match.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result("Not found.");
            return;
        }
        ShortenedUrl entry = match.get();
        if (entry.isDeleted()) {
            ctx.status(HttpStatus.GONE).result("This short URL has been removed.");
            return;
        }
        // Best-effort click counter bump. A DB hiccup here must never block
        // the redirect — analytics accuracy is strictly subordinate to
        // redirect availability.
        try {
            repository.incrementClicks(entry.id(), OffsetDateTime.now(clock));
        } catch (RuntimeException e) {
            log.warn("Couldn't bump click counter for token {}: {}", entry.token(), e.toString());
        }

        String url = entry.url();
        // Re-check the scheme on the way out, not just on the way in. Both
        // current write paths validate, so this can't fire today — but the
        // stored value lands in a Location header and an href, and a new insert
        // path or a hand-edited row would otherwise turn it straight into
        // javascript: in an anchor. Cheap enough to not depend on that.
        if (!UrlValidator.isValidHttpUrl(url)) {
            log.error("Refusing to redirect token {}: stored URL is not http(s).", entry.token());
            ctx.status(HttpStatus.NOT_FOUND).result("Not found.");
            return;
        }

        ctx.status(HttpStatus.MOVED_PERMANENTLY)
                .header("Location", url)
                .contentType("text/html; charset=utf-8")
                .result("<html>\n<body><a href=\"" + escapeAttr(url) + "\">moved here</a></body>\n");
    }

    /**
     * Shape check only — matches the generator's base36 alphabet and length.
     * A real lookup still decides whether the token exists.
     */
    private static boolean isPlausibleToken(String token) {
        if (token.length() != TokenGenerator.LENGTH) return false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            boolean base36 = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (!base36) return false;
        }
        return true;
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
