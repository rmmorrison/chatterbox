package ca.ryanmorrison.chatterbox.features.shortener;

import ca.ryanmorrison.chatterbox.http.HttpRouter;
import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

/**
 * URL shortener feature.
 *
 * <p>Slash command: {@code /shorten} with three subcommands:
 * <ul>
 *   <li>{@code create url:<URL>} — anyone; mints (or returns the existing)
 *       short URL for an HTTP(S) URL.</li>
 *   <li>{@code delete target:<URL or token>} — moderators only; soft-deletes
 *       a short URL after a button confirmation.</li>
 *   <li>{@code peek target:<URL or token> [share:<bool>]} — anyone; reveals
 *       a short URL's destination, ephemerally by default.</li>
 * </ul>
 *
 * <p>HTTP route: {@code GET /{token}} → 301 redirect to the original URL,
 * 410 Gone if the token has been soft-deleted, 404 if unknown. The bot-wide
 * HTTP server only binds when this (or another) module registers a route.
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
        return List.of(Commands.slash(ShortenerHandler.COMMAND, "Manage short URLs.")
                .addSubcommands(
                        new SubcommandData(ShortenerHandler.SUB_CREATE, "Shorten an HTTP(S) URL.")
                                .addOption(OptionType.STRING, ShortenerHandler.OPTION_URL,
                                        "The HTTP(S) URL to shorten.", true),
                        new SubcommandData(ShortenerHandler.SUB_DELETE,
                                "Soft-delete a short URL (Manage Messages required).")
                                .addOption(OptionType.STRING, ShortenerHandler.OPTION_TARGET,
                                        "Full short URL or 6-character short code.", true),
                        new SubcommandData(ShortenerHandler.SUB_PEEK,
                                "Show where a short URL points without following it.")
                                .addOption(OptionType.STRING, ShortenerHandler.OPTION_TARGET,
                                        "Full short URL or 6-character short code.", true)
                                .addOption(OptionType.BOOLEAN, ShortenerHandler.OPTION_SHARE,
                                        "Post the result to the channel instead of just to you.", false)));
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
        this.handler = new ShortenerHandler(repository, new TokenGenerator(), ShortenerModule::resolveBaseUrl);
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
