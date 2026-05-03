package ca.ryanmorrison.chatterbox.features.shortener;

import ca.ryanmorrison.chatterbox.http.HttpRouter;
import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * URL shortener feature.
 *
 * <p>Slash command: {@code /shorten <url>}. Stores HTTP(S) URLs against a
 * 6-character lowercase alphanumeric token, deduplicating by URL across users.
 *
 * <p>HTTP route: {@code GET /{token}} → 301 redirect to the original URL, or
 * 404 if unknown. The bot-wide HTTP server only binds when this (or another)
 * module registers a route.
 *
 * <p>Configuration: the public-facing prefix used to construct the link
 * returned to Discord users is read lazily from
 * {@code CHATTERBOX_SHORTENER_BASE_URL} when {@code /shorten} is first
 * invoked, so the bot can run without it set as long as the command isn't used.
 */
public final class ShortenerModule implements Module {

    static final String BASE_URL_ENV = "CHATTERBOX_SHORTENER_BASE_URL";

    private ShortenerHandler handler;
    private ShortenerRedirectHandler redirectHandler;

    @Override public String name() { return "shortener"; }

    @Override
    public List<String> migrationLocations() {
        return List.of("classpath:db/migration/shortener");
    }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        return List.of(Commands.slash(ShortenerHandler.COMMAND, "Shorten an HTTP(S) URL.")
                .addOption(OptionType.STRING, ShortenerHandler.OPTION_URL,
                        "The HTTP(S) URL to shorten.", true));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        ensureWired(ctx);
        return List.of(handler);
    }

    @Override
    public void registerHttpRoutes(HttpRouter router, InitContext ctx) {
        ensureWired(ctx);
        router.get(ShortenerRedirectHandler.PATH, redirectHandler);
    }

    private void ensureWired(InitContext ctx) {
        if (handler != null) return;
        var repository = new ShortenerRepository(ctx.database());
        this.handler = new ShortenerHandler(repository, new TokenGenerator(),
                new OpenGraphScraper(), ShortenerModule::resolveBaseUrl);
        this.redirectHandler = new ShortenerRedirectHandler(repository);
    }

    private static String resolveBaseUrl() {
        String v = System.getenv(BASE_URL_ENV);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(BASE_URL_ENV + " is not set.");
        }
        return v.trim();
    }
}
