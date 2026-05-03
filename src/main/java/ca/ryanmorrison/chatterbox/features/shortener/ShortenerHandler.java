package ca.ryanmorrison.chatterbox.features.shortener;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Handles {@code /shorten <url>}. Responsibilities:
 * <ol>
 *   <li>Validate the input URL (HTTP/HTTPS only).</li>
 *   <li>Return an existing token if the URL has been shortened before.</li>
 *   <li>Otherwise scrape OpenGraph metadata (best-effort, time-bounded),
 *       generate a token, insert, and retry on collision up to
 *       {@link #MAX_TOKEN_ATTEMPTS} times.</li>
 *   <li>Resolve the short URL prefix lazily — only fail when the command is
 *       actually invoked, so the bot can run without the shortener configured
 *       as long as nobody calls the command.</li>
 * </ol>
 *
 * <p>The reply is deferred up front because the OG scrape can take a couple of
 * seconds — well past Discord's 3-second initial-response window.
 */
final class ShortenerHandler extends ListenerAdapter {

    static final String COMMAND = "shorten";
    static final String OPTION_URL = "url";
    static final int MAX_TOKEN_ATTEMPTS = 10;

    private static final Logger log = LoggerFactory.getLogger(ShortenerHandler.class);

    private final ShortenerRepository repository;
    private final TokenGenerator tokenGenerator;
    private final OpenGraphScraper scraper;
    private final Supplier<String> baseUrlSupplier;

    ShortenerHandler(ShortenerRepository repository, TokenGenerator tokenGenerator,
                     OpenGraphScraper scraper, Supplier<String> baseUrlSupplier) {
        this.repository = repository;
        this.tokenGenerator = tokenGenerator;
        this.scraper = scraper;
        this.baseUrlSupplier = baseUrlSupplier;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) return;

        var urlOption = event.getOption(OPTION_URL);
        String url = urlOption == null ? null : urlOption.getAsString();

        if (!UrlValidator.isValidHttpUrl(url)) {
            event.reply("That doesn't look like a valid HTTP(S) URL.").setEphemeral(true).queue();
            return;
        }

        String baseUrl;
        try {
            baseUrl = baseUrlSupplier.get();
        } catch (RuntimeException e) {
            log.warn("Shortener invoked without a configured base URL.", e);
            event.reply("URL shortener is not configured on this bot.").setEphemeral(true).queue();
            return;
        }

        String trimmedUrl = url.trim();
        InteractionHook hook = event.deferReply(true).complete();

        Optional<ShortenedUrl> existing = repository.findByUrl(trimmedUrl);
        if (existing.isPresent()) {
            hook.editOriginal(buildShortUrl(baseUrl, existing.get().token())).queue();
            return;
        }

        OpenGraphMetadata metadata = scraper.scrape(trimmedUrl).orElse(OpenGraphMetadata.EMPTY);

        long userId = event.getUser().getIdLong();
        OffsetDateTime createdAt = event.getTimeCreated();

        for (int attempt = 0; attempt < MAX_TOKEN_ATTEMPTS; attempt++) {
            String token = tokenGenerator.next();
            Optional<ShortenedUrl> inserted = repository.insert(token, trimmedUrl, userId, createdAt, metadata);
            if (inserted.isPresent()) {
                hook.editOriginal(buildShortUrl(baseUrl, inserted.get().token())).queue();
                return;
            }
            // Either token collision (retry) or url race (re-read & return).
            Optional<ShortenedUrl> raced = repository.findByUrl(trimmedUrl);
            if (raced.isPresent()) {
                hook.editOriginal(buildShortUrl(baseUrl, raced.get().token())).queue();
                return;
            }
        }

        log.error("Exhausted {} token attempts shortening URL.", MAX_TOKEN_ATTEMPTS);
        hook.editOriginal("Failed to generate a unique short URL after several attempts. Please try again.").queue();
    }

    private static String buildShortUrl(String baseUrl, String token) {
        return baseUrl.endsWith("/") ? baseUrl + token : baseUrl + "/" + token;
    }
}
