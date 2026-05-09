package ca.ryanmorrison.chatterbox.features.shortener;

import ca.ryanmorrison.chatterbox.config.runtime.ConfigKey;
import ca.ryanmorrison.chatterbox.http.HttpRouter;
import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.List;
import java.util.Set;

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
 * <p>Auto-shortener: an {@link AutoShortenListener} is always installed and
 * consults {@link ca.ryanmorrison.chatterbox.config.runtime.RuntimeConfig}
 * per-guild on every message. The keys it reads — {@code autoshorten.enabled}
 * and {@code autoshorten.threshold} — are server-overridable via the
 * {@code /config} slash command and fall back to {@code CHATTERBOX_AUTOSHORTEN_ENABLED}
 * / {@code CHATTERBOX_AUTOSHORTEN_THRESHOLD} env vars (and finally
 * {@code true} / {@code 160} defaults). Requires
 * {@link net.dv8tion.jda.api.Permission#MESSAGE_MANAGE} in each channel;
 * without it the listener is a no-op.
 *
 * <p>Configuration: the public-facing prefix used to construct the link
 * returned to Discord users is read lazily from
 * {@code CHATTERBOX_SHORTENER_BASE_URL} when {@code /shorten} is first
 * invoked, so the bot can run without it set as long as no shortening
 * happens. The auto-shortener simply skips messages while it's unset.
 */
public final class ShortenerModule implements Module {

    static final String BASE_URL_ENV = "CHATTERBOX_SHORTENER_BASE_URL";

    static final ConfigKey<Boolean> AUTOSHORTEN_ENABLED = ConfigKey.bool(
            "autoshorten.enabled",
            "CHATTERBOX_AUTOSHORTEN_ENABLED",
            "true",
            "When true, the bot replaces long HTTP(S) URLs in this server's messages with short links.");

    static final ConfigKey<Integer> AUTOSHORTEN_THRESHOLD = ConfigKey.positiveInt(
            "autoshorten.threshold",
            "CHATTERBOX_AUTOSHORTEN_THRESHOLD",
            "160",
            "Minimum URL length (characters) before auto-shortening kicks in.");

    private ShortenerHandler handler;
    private ShortenerRedirectHandler redirectHandler;
    private AutoShortenListener autoShortenListener;

    @Override public String name() { return "shortener"; }

    /**
     * The auto-shortener needs to read message bodies, which requires the
     * privileged {@code MESSAGE_CONTENT} intent. Always request it (and
     * {@code GUILD_MESSAGES}); toggling the {@code autoshorten.enabled} key
     * just makes the listener short-circuit per-guild without re-deploying
     * with different intents.
     */
    @Override
    public Set<GatewayIntent> intents() {
        return Set.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
    }

    @Override
    public List<ConfigKey<?>> configKeys() {
        return List.of(AUTOSHORTEN_ENABLED, AUTOSHORTEN_THRESHOLD);
    }

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
                                        "Post the result to the channel instead of just to you.", false),
                        new SubcommandData(ShortenerHandler.SUB_STATS,
                                "Show click stats for a short URL.")
                                .addOption(OptionType.STRING, ShortenerHandler.OPTION_TARGET,
                                        "Full short URL or 6-character short code.", true)));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        ensureWired(ctx);
        return List.of(handler, autoShortenListener);
    }

    @Override
    public void registerHttpRoutes(HttpRouter router, InitContext ctx) {
        ensureWired(ctx);
        router.get(ShortenerRedirectHandler.PATH, redirectHandler);
    }

    private void ensureWired(InitContext ctx) {
        if (handler != null) return;
        var repository = new ShortenerRepository(ctx.database());
        var tokenGenerator = new TokenGenerator();
        this.handler = new ShortenerHandler(repository, tokenGenerator, ShortenerModule::resolveBaseUrl);
        this.redirectHandler = new ShortenerRedirectHandler(repository);
        // Listener is registered unconditionally — per-guild enabled/threshold
        // are looked up at message-receive time via RuntimeConfig.
        this.autoShortenListener = new AutoShortenListener(
                repository, tokenGenerator, ShortenerModule::resolveBaseUrl, ctx.runtimeConfig());
    }

    private static String resolveBaseUrl() {
        String v = System.getenv(BASE_URL_ENV);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(BASE_URL_ENV + " is not set.");
        }
        return v.trim();
    }
}
