package ca.ryanmorrison.chatterbox.features.shortener;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;

import java.util.Locale;
import java.util.Optional;

/**
 * Javalin handler for {@code GET /{token}}. Returns 301 with the original URL
 * in the {@code Location} header on hit, 404 on miss. Tokens are looked up in
 * lowercase since the alphabet is case-insensitive.
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
        ctx.status(HttpStatus.MOVED_PERMANENTLY).header("Location", match.get().url());
    }
}
