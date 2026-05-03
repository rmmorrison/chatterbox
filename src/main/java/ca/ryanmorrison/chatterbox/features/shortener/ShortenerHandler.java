package ca.ryanmorrison.chatterbox.features.shortener;

import ca.ryanmorrison.chatterbox.common.permissions.Permissions;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Handles all interactions for the {@code /shorten} command. Subcommands:
 * <ul>
 *   <li>{@code create url:<URL>} — anyone; mints (or returns the existing)
 *       short URL.</li>
 *   <li>{@code delete target:<URL or token>} — moderators only ({@link
 *       Permissions#requireManageMessages}); soft-deletes after a button
 *       confirmation showing destination, creator, and creation time.</li>
 *   <li>{@code peek target:<URL or token> [share:<bool>]} — anyone; reveals
 *       the destination URL. Ephemeral by default; the caller can opt in to a
 *       channel-visible reply with {@code share:true}.</li>
 * </ul>
 *
 * <p>Also handles the confirm/cancel buttons attached to delete prompts. The
 * row id is encoded in the component id, and the moderator permission is
 * re-checked on the click in case the role was lost between rendering and
 * confirming.
 *
 * <p>Resolves the public-facing base URL lazily — callers that don't use the
 * shortener never trigger the {@code CHATTERBOX_SHORTENER_BASE_URL} requirement.
 */
final class ShortenerHandler extends ListenerAdapter {

    static final String COMMAND = "shorten";
    static final String SUB_CREATE = "create";
    static final String SUB_DELETE = "delete";
    static final String SUB_PEEK = "peek";
    static final String OPTION_URL = "url";
    static final String OPTION_TARGET = "target";
    static final String OPTION_SHARE = "share";
    static final int MAX_TOKEN_ATTEMPTS = 10;

    static final String BUTTON_DELETE_CONFIRM_PREFIX = "shorten:delete-confirm:";
    static final String BUTTON_DELETE_CANCEL_PREFIX = "shorten:delete-cancel:";

    private static final String NOT_FOUND_MESSAGE =
            "No short URL matches that target.";
    private static final String NOT_CONFIGURED_MESSAGE =
            "URL shortener is not configured on this bot.";
    private static final String INVALID_TARGET_MESSAGE =
            "Target must be a 6-character short code or a full short URL.";

    private static final Logger log = LoggerFactory.getLogger(ShortenerHandler.class);

    private final ShortenerRepository repository;
    private final TokenGenerator tokenGenerator;
    private final Supplier<String> baseUrlSupplier;

    ShortenerHandler(ShortenerRepository repository, TokenGenerator tokenGenerator,
                     Supplier<String> baseUrlSupplier) {
        this.repository = repository;
        this.tokenGenerator = tokenGenerator;
        this.baseUrlSupplier = baseUrlSupplier;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) return;
        String sub = event.getSubcommandName();
        if (sub == null) return;
        switch (sub) {
            case SUB_CREATE -> handleCreate(event);
            case SUB_DELETE -> handleDelete(event);
            case SUB_PEEK   -> handlePeek(event);
            default -> { /* unknown subcommand — silently ignore */ }
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id.startsWith(BUTTON_DELETE_CONFIRM_PREFIX)) {
            handleDeleteConfirm(event, id.substring(BUTTON_DELETE_CONFIRM_PREFIX.length()));
        } else if (id.startsWith(BUTTON_DELETE_CANCEL_PREFIX)) {
            handleDeleteCancel(event);
        }
    }

    // -- create -------------------------------------------------------------

    private void handleCreate(SlashCommandInteractionEvent event) {
        OptionMapping urlOption = event.getOption(OPTION_URL);
        String url = urlOption == null ? null : urlOption.getAsString();

        if (!UrlValidator.isValidHttpUrl(url)) {
            event.reply("That doesn't look like a valid HTTP(S) URL.").setEphemeral(true).queue();
            return;
        }

        String baseUrl = resolveBaseUrlOrReply(event);
        if (baseUrl == null) return;

        String trimmedUrl = url.trim();

        Optional<ShortenedUrl> existing = repository.findByUrl(trimmedUrl);
        if (existing.isPresent()) {
            event.reply(buildShortUrl(baseUrl, existing.get().token())).setEphemeral(true).queue();
            return;
        }

        long userId = event.getUser().getIdLong();
        OffsetDateTime createdAt = event.getTimeCreated();

        for (int attempt = 0; attempt < MAX_TOKEN_ATTEMPTS; attempt++) {
            String token = tokenGenerator.next();
            Optional<ShortenedUrl> inserted = repository.insert(token, trimmedUrl, userId, createdAt);
            if (inserted.isPresent()) {
                event.reply(buildShortUrl(baseUrl, inserted.get().token())).setEphemeral(true).queue();
                return;
            }
            // Either token collision (retry) or url race (re-read & return).
            Optional<ShortenedUrl> raced = repository.findByUrl(trimmedUrl);
            if (raced.isPresent()) {
                event.reply(buildShortUrl(baseUrl, raced.get().token())).setEphemeral(true).queue();
                return;
            }
        }

        log.error("Exhausted {} token attempts shortening URL.", MAX_TOKEN_ATTEMPTS);
        event.reply("Failed to generate a unique short URL after several attempts. Please try again.")
                .setEphemeral(true).queue();
    }

    // -- delete -------------------------------------------------------------

    private void handleDelete(SlashCommandInteractionEvent event) {
        if (!Permissions.requireManageMessages(event)) return;

        String baseUrl = resolveBaseUrlOrReply(event);
        if (baseUrl == null) return;

        String target = optionString(event, OPTION_TARGET);
        Optional<String> token = TargetParser.extractToken(target, baseUrl);
        if (token.isEmpty()) {
            event.reply(INVALID_TARGET_MESSAGE).setEphemeral(true).queue();
            return;
        }

        Optional<ShortenedUrl> match = repository.findByToken(token.get());
        if (match.isEmpty()) {
            event.reply(NOT_FOUND_MESSAGE).setEphemeral(true).queue();
            return;
        }

        ShortenedUrl entry = match.get();
        event.replyEmbeds(buildDeleteConfirmEmbed(entry, baseUrl))
                .setEphemeral(true)
                .setComponents(ActionRow.of(
                        Button.danger(BUTTON_DELETE_CONFIRM_PREFIX + entry.id(), "Delete"),
                        Button.secondary(BUTTON_DELETE_CANCEL_PREFIX + entry.id(), "Cancel")))
                .queue();
    }

    private void handleDeleteConfirm(ButtonInteractionEvent event, String idStr) {
        if (!Permissions.requireManageMessages(event)) return;

        long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            event.editMessage("Invalid delete request.").setComponents().queue();
            return;
        }

        Optional<ShortenedUrl> entry = repository.findByIdIncludingDeleted(id);
        if (entry.isEmpty()) {
            event.editMessage("That short URL no longer exists.").setEmbeds().setComponents().queue();
            return;
        }
        if (entry.get().isDeleted()) {
            event.editMessage("Already deleted.").setEmbeds().setComponents().queue();
            return;
        }

        long deletedBy = event.getUser().getIdLong();
        OffsetDateTime deletedAt = event.getTimeCreated();
        int updated = repository.softDelete(id, deletedBy, deletedAt);
        if (updated == 0) {
            // Race: someone else just deleted it.
            event.editMessage("Already deleted.").setEmbeds().setComponents().queue();
            return;
        }

        event.editMessage("Deleted `" + entry.get().token() + "`.").setEmbeds().setComponents().queue();
    }

    private void handleDeleteCancel(ButtonInteractionEvent event) {
        event.editMessage("Cancelled.").setEmbeds().setComponents().queue();
    }

    // -- peek ---------------------------------------------------------------

    private void handlePeek(SlashCommandInteractionEvent event) {
        String baseUrl = resolveBaseUrlOrReply(event);
        if (baseUrl == null) return;

        String target = optionString(event, OPTION_TARGET);
        Optional<String> token = TargetParser.extractToken(target, baseUrl);
        if (token.isEmpty()) {
            event.reply(INVALID_TARGET_MESSAGE).setEphemeral(true).queue();
            return;
        }

        Optional<ShortenedUrl> match = repository.findByToken(token.get());
        if (match.isEmpty()) {
            event.reply(NOT_FOUND_MESSAGE).setEphemeral(true).queue();
            return;
        }

        boolean share = optionBoolean(event, OPTION_SHARE, false);
        ShortenedUrl entry = match.get();
        String reply = "`" + buildShortUrl(baseUrl, entry.token()) + "` → " + entry.url();
        event.reply(reply).setEphemeral(!share).queue();
    }

    // -- helpers ------------------------------------------------------------

    private String resolveBaseUrlOrReply(net.dv8tion.jda.api.interactions.callbacks.IReplyCallback event) {
        try {
            return baseUrlSupplier.get();
        } catch (RuntimeException e) {
            log.warn("Shortener invoked without a configured base URL.", e);
            event.reply(NOT_CONFIGURED_MESSAGE).setEphemeral(true).queue();
            return null;
        }
    }

    private static String optionString(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt == null ? null : opt.getAsString();
    }

    private static boolean optionBoolean(SlashCommandInteractionEvent event, String name, boolean fallback) {
        OptionMapping opt = event.getOption(name);
        return opt == null ? fallback : opt.getAsBoolean();
    }

    private static String buildShortUrl(String baseUrl, String token) {
        return baseUrl.endsWith("/") ? baseUrl + token : baseUrl + "/" + token;
    }

    private static net.dv8tion.jda.api.entities.MessageEmbed buildDeleteConfirmEmbed(
            ShortenedUrl entry, String baseUrl) {
        long createdAtSeconds = entry.createdAt().toEpochSecond();
        return new EmbedBuilder()
                .setTitle("Delete short URL?")
                .setColor(0xE74C3C)
                .addField("Short URL", "`" + buildShortUrl(baseUrl, entry.token()) + "`", false)
                .addField("Destination", entry.url(), false)
                .addField("Created by", "<@" + entry.createdBy() + ">", true)
                .addField("Created", "<t:" + createdAtSeconds + ":f> (<t:" + createdAtSeconds + ":R>)", true)
                .setFooter("This cannot be undone. The token will not be reissued.")
                .build();
    }
}
