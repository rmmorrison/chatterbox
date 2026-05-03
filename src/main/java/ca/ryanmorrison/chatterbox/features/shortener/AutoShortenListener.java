package ca.ryanmorrison.chatterbox.features.shortener;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Listens for guild messages and auto-shortens long HTTP(S) URLs.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>Skip messages from bots, system events, DMs, and the bot itself.</li>
 *   <li>Tokenise the content via {@link MessageRewriter}; this isolates
 *       URLs that aren't inside code spans/blocks, spoilers, markdown
 *       links, or angle-bracket opt-outs.</li>
 *   <li>For each candidate URL, mint (or look up) a short token via the
 *       shared {@link ShortenerRepository}, attributing it to the original
 *       author and original message timestamp.</li>
 *   <li>Post a replacement message (mention-suppressed) under the bot's
 *       identity, then delete the original. Replacement is posted first so a
 *       failed post never erases the user's text.</li>
 * </ol>
 *
 * <h2>Skip conditions</h2>
 * The listener is a no-op when:
 * <ul>
 *   <li>{@code CHATTERBOX_AUTOSHORTEN_ENABLED} is false (handled by
 *       {@link ShortenerModule} not registering us).</li>
 *   <li>{@code CHATTERBOX_SHORTENER_BASE_URL} is unset — without it we
 *       can't construct the replacement URL.</li>
 *   <li>The bot lacks {@link Permission#MESSAGE_MANAGE} in the channel.
 *       Logged at WARN once per call.</li>
 *   <li>The rewritten message would exceed Discord's 2000-character cap.</li>
 *   <li>None of the URLs in the message are over the configured threshold.</li>
 *   <li>The URL is already on the configured short-URL prefix (don't
 *       re-shorten ourselves).</li>
 * </ul>
 *
 * <h2>Failure ordering</h2>
 * Replacement posted first, original deleted only if the post succeeded.
 * If the delete subsequently fails, both messages exist briefly and a
 * moderator can clean up — preferable to silently erasing the user's text.
 */
final class AutoShortenListener extends ListenerAdapter {

    static final int MAX_TOKEN_ATTEMPTS = 10;
    static final int DISCORD_MESSAGE_LIMIT = 2000;

    private static final Logger log = LoggerFactory.getLogger(AutoShortenListener.class);

    private final ShortenerRepository repository;
    private final TokenGenerator tokenGenerator;
    private final Supplier<String> baseUrlSupplier;
    private final int threshold;

    AutoShortenListener(ShortenerRepository repository, TokenGenerator tokenGenerator,
                        Supplier<String> baseUrlSupplier, int threshold) {
        this.repository = repository;
        this.tokenGenerator = tokenGenerator;
        this.baseUrlSupplier = baseUrlSupplier;
        this.threshold = threshold;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        if (event.getAuthor().isBot()) return;
        if (event.isWebhookMessage()) return;
        if (event.getAuthor().getIdLong() == event.getJDA().getSelfUser().getIdLong()) return;

        Message message = event.getMessage();
        String content = message.getContentRaw();
        if (content.isBlank()) return;

        String baseUrl = resolveBaseUrl();
        if (baseUrl == null) return;

        List<MessageRewriter.Span> spans = MessageRewriter.tokenize(content);

        // Walk the spans, deciding which URLs to shorten. Same URL repeated in
        // one message hits the shortener once and is replaced everywhere.
        Map<String, String> substitutions = new LinkedHashMap<>();
        for (MessageRewriter.Span span : spans) {
            if (!(span instanceof MessageRewriter.Span.ShortenableUrl(String url))) continue;
            if (substitutions.containsKey(url)) continue;
            if (url.length() < threshold) continue;
            if (isAlreadyShort(url, baseUrl)) continue;

            Optional<String> token = mintToken(url, event.getAuthor().getIdLong(), message.getTimeCreated());
            if (token.isEmpty()) continue;
            substitutions.put(url, buildShortUrl(baseUrl, token.get()));
        }

        if (substitutions.isEmpty()) return;

        String rewritten = MessageRewriter.rewrite(spans, url -> Optional.ofNullable(substitutions.get(url)));
        if (rewritten.equals(content)) return;

        String authorMention = event.getAuthor().getAsMention();
        String fullMessage = authorMention + ": " + rewritten;
        if (fullMessage.length() > DISCORD_MESSAGE_LIMIT) {
            log.debug("Skipping auto-shorten — rewritten message would exceed Discord's {} char cap.",
                    DISCORD_MESSAGE_LIMIT);
            return;
        }

        GuildMessageChannel channel = event.getGuildChannel();
        Member self = event.getGuild().getSelfMember();
        if (!self.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            log.warn("Lacking MESSAGE_MANAGE in channel {} (#{}); skipping auto-shorten. "
                            + "Grant the bot Manage Messages to enable this feature.",
                    channel.getId(), channel.getName());
            return;
        }

        var post = channel.sendMessage(fullMessage)
                .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class));
        Message reply = message.getReferencedMessage();
        if (reply != null) {
            post = post.setMessageReference(reply).failOnInvalidReply(false);
        }
        post.queue(
                posted -> message.delete().queue(
                        v -> {},
                        err -> log.warn("Auto-shortened message {} but couldn't delete original {}: {}",
                                posted.getId(), message.getId(), err.toString())),
                err -> log.warn("Failed to post auto-shortened replacement for {}: {}",
                        message.getId(), err.toString()));
    }

    private String resolveBaseUrl() {
        try {
            return baseUrlSupplier.get();
        } catch (RuntimeException e) {
            log.debug("Auto-shorten skipped: shortener base URL not configured.");
            return null;
        }
    }

    private Optional<String> mintToken(String url, long createdBy, OffsetDateTime createdAt) {
        Optional<ShortenedUrl> existing = repository.findByUrl(url);
        if (existing.isPresent()) return Optional.of(existing.get().token());

        for (int attempt = 0; attempt < MAX_TOKEN_ATTEMPTS; attempt++) {
            String token = tokenGenerator.next();
            Optional<ShortenedUrl> inserted = repository.insert(token, url, createdBy, createdAt);
            if (inserted.isPresent()) return Optional.of(inserted.get().token());
            // Either token collision (retry) or url race (re-read & return).
            Optional<ShortenedUrl> raced = repository.findByUrl(url);
            if (raced.isPresent()) return Optional.of(raced.get().token());
        }
        log.error("Exhausted {} token attempts auto-shortening URL.", MAX_TOKEN_ATTEMPTS);
        return Optional.empty();
    }

    static boolean isAlreadyShort(String url, String baseUrl) {
        String normalisedBase = baseUrl.toLowerCase(Locale.ROOT);
        if (!normalisedBase.endsWith("/")) normalisedBase = normalisedBase + "/";
        return url.toLowerCase(Locale.ROOT).startsWith(normalisedBase);
    }

    private static String buildShortUrl(String baseUrl, String token) {
        return baseUrl.endsWith("/") ? baseUrl + token : baseUrl + "/" + token;
    }
}
