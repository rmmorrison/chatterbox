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
